package com.shippingadaptor.orders.service.impl;

import com.shippingadaptor.common.enums.MasterStoresEnum;
import com.shippingadaptor.common.enums.OrdersTimeFilterEnum;
import com.shippingadaptor.config.security.AuthenticationFacadeImpl;
import com.shippingadaptor.dto.*;
import com.shippingadaptor.integration.ecommerce.shipstation.model.ShipStation;
import com.shippingadaptor.integration.ecommerce.shopify.model.*;
import com.shippingadaptor.integration.ecommerce.shopify.param.OrderListParam;
import com.shippingadaptor.models.*;
import com.shippingadaptor.orders.repository.OrderLineItemRepository;
import com.shippingadaptor.orders.repository.OrderShippingAddressRepository;
import com.shippingadaptor.orders.repository.OrderShippingLinesRepository;
import com.shippingadaptor.orders.repository.OrdersRepository;
import com.shippingadaptor.orders.service.OrdersService;
import com.shippingadaptor.store.repository.MerchantStoreMetadataViewRepository;
import com.shippingadaptor.store.repository.MerchantStoreViewRepository;
import com.shippingadaptor.user.service.UserService;
import com.shippingadaptor.utility.Constant;
import com.shippingadaptor.utility.DateUtility;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * OrdersServiceImpl
 */
@Service
public class OrdersServiceImpl implements OrdersService {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationFacadeImpl authenticationFacade;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private MerchantStoreViewRepository merchantStoreViewRepository;

    @Autowired
    private MerchantStoreMetadataViewRepository merchantStoreMetadataViewRepository;

    @Autowired
    private OrderShippingAddressRepository orderShippingAddressRepository;

    @Autowired
    private OrderShippingLinesRepository orderShippingLinesRepository;

    @Autowired
    private OrderLineItemRepository orderLineItemRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * importOrdersFromEcomPlatform
     */
    @Transactional
    @Override
    public void importOrdersFromEcomPlatform() {

        User systemUser = this.userService.findUserUserByUserId(Constant.SYSTEM_USER_ID);
        User user = this.userService.findUserByUserName(this.authenticationFacade.getUserName());

        List<MerchantStoreDTO> merchantStoreList = modelMapper.map(this.merchantStoreViewRepository.findAllByUserId(this.userService.findUserByUserName(this.authenticationFacade.getUserName()).getUserID()), new TypeToken<List<MerchantStoreDTO>>() {
        }.getType());

        for (MerchantStoreDTO merchantStoreDTO : merchantStoreList) {
            merchantStoreDTO.setMerchantStoreConnectionList(modelMapper.map(merchantStoreMetadataViewRepository.findAllByMerchantStoreId(merchantStoreDTO.getMerchantStoreId()), new TypeToken<List<MerchantStoreConnectionDTO>>() {
            }.getType()));
        }
        for (MerchantStoreDTO merchantStore : merchantStoreList) {
            List<String> ecomPlatformOrderIds = ordersRepository.findLastOrdersEcomPlatformOrderId(new MerchantStore(merchantStore.getMerchantStoreId()), PageRequest.of(0, 1)).getContent();
            if (MasterStoresEnum.SHIP_STATION.getMasterStoreId() == merchantStore.getStoreMasterId()) {
                String apiKey = merchantStore.getMerchantStoreConnectionList().stream().filter(merchantStoreConnectionDTO -> merchantStoreConnectionDTO.getParamName().equals(ShipStation.PARAM_API_KEY)).map(MerchantStoreConnectionDTO::getParamValue).findAny().orElse(null);
                String apiSecret = merchantStore.getMerchantStoreConnectionList().stream().filter(merchantStoreConnectionDTO -> merchantStoreConnectionDTO.getParamName().equals(ShipStation.PARAM_API_SECRET)).map(MerchantStoreConnectionDTO::getParamValue).findAny().orElse(null);

            } else if (MasterStoresEnum.SHOPIFY.getMasterStoreId() == merchantStore.getStoreMasterId()) {
                String accessToken = merchantStore.getMerchantStoreConnectionList().stream().filter(merchantStoreConnectionDTO -> merchantStoreConnectionDTO.getParamName().equals(Shopify.PARAM_ACCESS_TOKEN)).map(MerchantStoreConnectionDTO::getParamValue).findAny().orElse(null);
                String storeURL = merchantStore.getMerchantStoreConnectionList().stream().filter(merchantStoreConnectionDTO -> merchantStoreConnectionDTO.getParamName().equals(Shopify.PARAM_STORE_URL)).map(MerchantStoreConnectionDTO::getParamValue).findAny().orElse(null);
                OrdersList ordersList;
                if (ecomPlatformOrderIds.isEmpty()) {
                    ordersList = ShopifyClient.builder().setApiCredentials(accessToken, storeURL).build().getOrderClient().listOrders(OrderListParam.builder().build());
                } else {
                    ordersList = ShopifyClient.builder().setApiCredentials(accessToken, storeURL).build().getOrderClient().listOrders(OrderListParam.builder().setSinceId(ecomPlatformOrderIds.get(0)).build());
                }

                Orders orders;
                OrderShippingAddress orderShippingAddress;
                OrderShippingLines orderShippingLines;
                OrderLineItem orderLineItem;
                if (ordersList.getOrders() != null && ordersList.getErrors() == null) {
                    for (Order shopifyOrder : ordersList.getOrders()) {
                        orders = modelMapper.map(shopifyOrder, new TypeToken<Orders>() {
                        }.getType());
                        orders.setEcomPlatformOrderId(String.valueOf(shopifyOrder.getId()));
                        orders.setUser(user);
                        orders.setStoreMaster(new StoreMaster(merchantStore.getStoreMasterId()));
                        orders.setMerchantStore(new MerchantStore(merchantStore.getMerchantStoreId()));
                        orders.setCustomerEmail(shopifyOrder.getContactEmail());

                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

                        orders.setOrderCreatedAt(LocalDateTime.parse(shopifyOrder.getCreatedAt(), formatter));
                        orders.setFulfillmentStatus(shopifyOrder.getFulfillmentStatus());
                        orders.setOrderName(shopifyOrder.getName());
                        orders.setNote(shopifyOrder.getNote());
                        orders.setCreatedOn(LocalDateTime.now());
                        orders.setCreatedBy(systemUser);
                        orders.setUpdatedOn(LocalDateTime.now());
                        orders.setUpdatedBy(systemUser);

                        ordersRepository.save(orders);

                        if (shopifyOrder.getShippingAddress() != null) {
                            orderShippingAddress = modelMapper.map(shopifyOrder.getShippingAddress(), new TypeToken<OrderShippingAddress>() {
                            }.getType());

                            orderShippingAddress.setOrder(orders);
                            orderShippingAddress.setCreatedOn(LocalDateTime.now());
                            orderShippingAddress.setCreatedBy(systemUser);
                            orderShippingAddress.setUpdatedOn(LocalDateTime.now());
                            orderShippingAddress.setUpdatedBy(systemUser);

                            orderShippingAddressRepository.save(orderShippingAddress);
                        }

                        if (shopifyOrder.getShippingLines() != null) {
                            for (ShippingLine shippingLine : shopifyOrder.getShippingLines()) {
                                orderShippingLines = modelMapper.map(shippingLine, new TypeToken<OrderShippingLines>() {
                                }.getType());

                                orderShippingLines.setOrder(orders);
                                orderShippingLines.setCreatedOn(LocalDateTime.now());
                                orderShippingLines.setCreatedBy(systemUser);
                                orderShippingLines.setUpdatedOn(LocalDateTime.now());
                                orderShippingLines.setUpdatedBy(systemUser);

                                orderShippingLinesRepository.save(orderShippingLines);
                            }

                        }

                        if (shopifyOrder.getLineItems() != null) {
                            for (LineItem lineItem : shopifyOrder.getLineItems()) {
                                orderLineItem = modelMapper.map(lineItem, new TypeToken<OrderLineItem>() {
                                }.getType());

                                orderLineItem.setOrder(orders);
                                orderLineItem.setLineItemId(String.valueOf(lineItem.getId()));
                                orderLineItem.setWeight(BigDecimal.valueOf(lineItem.getGrams()));
                                if (orderLineItem.getSku() == null) {
                                    orderLineItem.setSku("IPOD2008GREEN");
                                }
                                orderLineItem.setCreatedOn(LocalDateTime.now());
                                orderLineItem.setCreatedBy(systemUser);
                                orderLineItem.setUpdatedOn(LocalDateTime.now());
                                orderLineItem.setUpdatedBy(systemUser);

                                orderLineItemRepository.save(orderLineItem);
                            }

                        }

                    }
                }


            }
        }
    }

    public OrdersReponseDTO getAllOrders(OrderSearchRequest orderSearchRequest) {

        populateDates(orderSearchRequest);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Orders> cq = cb.createQuery(Orders.class);
        Root<Orders> root = cq.from(Orders.class);
        cq.select(root);
        List<Predicate> predicateConditions = new ArrayList<>();
        if (!orderSearchRequest.getFromDate().isEmpty() && orderSearchRequest.getToDate().isEmpty()) {
            orderSearchRequest.setToDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern(DateUtility.DATE_FORMAT_MMDDYYYY)));
        }
        if (!orderSearchRequest.getFromDate().isEmpty() && !orderSearchRequest.getToDate().isEmpty()) {
            predicateConditions.add(new OrderSearchSpecification.OrderBetweenDate(orderSearchRequest.getFromLocalDate(), orderSearchRequest.getToLocalDate()).toPredicate(root, cq, cb));
        }
        if (!orderSearchRequest.getStoreMasterId().isEmpty()) {
            predicateConditions.add(new OrderSearchSpecification.OrderHasStoreMaster(Long.parseLong(orderSearchRequest.getStoreMasterId())).toPredicate(root, cq, cb));
        }
        if (!orderSearchRequest.getMerchantStoreId().isEmpty()) {
            predicateConditions.add(new OrderSearchSpecification.OrderHasStoreMaster(Long.parseLong(orderSearchRequest.getStoreMasterId())).toPredicate(root, cq, cb));
        }
        cq.where(predicateConditions.toArray(new Predicate[]{}));
        List<Orders> ordersList = entityManager.createQuery(cq).getResultList();

        OrdersReponseDTO ordersReponseDTO = new OrdersReponseDTO();
        List<OrdersResultDTO> ordersResultDTOList = new ArrayList<>();
        for (Orders orders : ordersList) {
            OrdersResultDTO ordersResultDTO = modelMapper.map(orders, new TypeToken<OrdersResultDTO>() {
            }.getType());
            ordersResultDTO.setOrderShippingAddress(orderShippingAddressRepository.findByOrder(orders));
            ordersResultDTO.setStoreName(orders.getMerchantStore().getStoreName());
            ordersResultDTO.setMasterStoreName(orders.getStoreMaster().getStoreName());
            ordersResultDTO.setShippingSource(orderShippingLinesRepository.findByOrder(orders, PageRequest.of(0, 1)).getContent().get(0).getSource());
            ordersResultDTOList.add(ordersResultDTO);
        }
        ordersReponseDTO.setResult(ordersResultDTOList);
        return ordersReponseDTO;
    }

    private void populateDates(OrderSearchRequest orderSearchRequest) {
        if (orderSearchRequest.getTimeFilter().equals(OrdersTimeFilterEnum.ALL.getValue())) {
            orderSearchRequest.setFromDate("");
            orderSearchRequest.setToDate("");
        } else if (orderSearchRequest.getTimeFilter().equals(OrdersTimeFilterEnum.TODAY.getValue())) {
            orderSearchRequest.setFromDate(LocalDate.now().format(DateTimeFormatter.ofPattern(DateUtility.DATE_FORMAT_MMDDYYYY)));
            orderSearchRequest.setToDate(LocalDate.now().format(DateTimeFormatter.ofPattern(DateUtility.DATE_FORMAT_MMDDYYYY)));
        }
        else if(orderSearchRequest.getTimeFilter().equals(OrdersTimeFilterEnum.THIS_WEEK.getValue()))
        {

        }

    }
}
