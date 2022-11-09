package com.shippingadaptor.orders.service.impl;

import com.shippingadaptor.boxing.service.BoxingService;
import com.shippingadaptor.common.enums.MasterStoresEnum;
import com.shippingadaptor.config.security.AuthenticationFacadeImpl;
import com.shippingadaptor.dto.MerchantStoreConnectionDTO;
import com.shippingadaptor.dto.MerchantStoreDTO;
import com.shippingadaptor.integration.ecommerce.shipstation.model.ShipStation;
import com.shippingadaptor.integration.ecommerce.shopify.model.*;
import com.shippingadaptor.integration.ecommerce.shopify.param.OrderListParam;
import com.shippingadaptor.integration.shippingcarrier.eshipper.models.*;
import com.shippingadaptor.integration.shippingcarrier.eshipper.models.Box;
import com.shippingadaptor.models.*;
import com.shippingadaptor.orders.repository.OrdersRepository;
import com.shippingadaptor.orders.service.OrdersService;
import com.shippingadaptor.store.repository.MerchantStoreConnectionRepository;
import com.shippingadaptor.store.repository.MerchantStoreMetadataViewRepository;
import com.shippingadaptor.store.repository.MerchantStoreViewRepository;
import com.shippingadaptor.user.service.UserService;
import com.shippingadaptor.utility.Constant;
import com.shippingadaptor.utility.MessageKey;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private MerchantStoreConnectionRepository merchantStoreConnectionRepository;

    @Autowired
    private MerchantStoreMetadataViewRepository merchantStoreMetadataViewRepository;

    @Autowired
    private BoxingService boxingService;
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

                        //ordersRepository.save(orders);
                        Items items=new Items();
                        List<Item> itemList = new ArrayList<>();
                        ShopifyClient shopifyClient=ShopifyClient.builder().setApiCredentials(accessToken, storeURL).build();
                        for(LineItem lineItem:shopifyOrder.getLineItems())
                        {
                            VariantResponse variantResponse = shopifyClient.getVariantClient().getVariant(lineItem.getVariantId());
                            InventoryItemResponse inventoryItemResponse=shopifyClient.getInventoryItemClient().getInventoryItem(variantResponse.getVariant().getInventoryItemId());

                            Item item=modelMapper.map(lineItem,Item.class);
                            item.setHscode(inventoryItemResponse.getInventoryItem().getHarmonizedSystemCode().toString());
                            item.setLength("4");
                            item.setHeight("4");
                            item.setWidth("4");
                            item.setWeight("4");

                            itemList.add(item);

                        }
                        items.setItems(itemList);
                        BoxingRequest boxingRequest = new BoxingRequest();
                        Boxes boxes = new Boxes();


                        if (this.boxingService.existsByUser(this.userService.findUserByUserName(this.authenticationFacade.getUserName()))) {
                            boxes.setBoxes(modelMapper.map(this.boxingService.findByUserAllBox(), new TypeToken<List<Box>>() {}.getType()));
                            System.out.println("reached");
                        }

                        boxingRequest.setBoxes(boxes);
                        boxingRequest.setItems(items);

                        EShipper eShipper = EShipperClient.builder().setEShipperCredentials("rahul_test", "admint7102").build().getBoxingClient().listBoxing(boxingRequest);
                        for(Box box:eShipper.getBoxingRequestReply().getPackedBoxes().getBoxes())
                        {
                            BoxingResult boxingResult=modelMapper.map(box,BoxingResult.class);
                            boxingResult.setOrderLineItem(box.getItems());
                            System.out.println("mapped");
                        }

                    }
                }


            }
        }
    }
}
