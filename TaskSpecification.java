package com.narola.taskservice.repository;

import com.narola.core.entity.User;
import com.narola.core.entity.User_;
import com.narola.taskservice.entity.*;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.List;

public class TaskSpecification {

    public static Specification<Task> taskHasProjectId(int projectId) {
        return (root, query, builder) -> {
            Join<TaskMaster, Project> taskMasterProjectJoin = root.join(Task_.taskMaster).join(TaskMaster_.project);
            return builder.equal(taskMasterProjectJoin.get(Project_.ID), projectId);
        };
    }

    public static Specification<Task> taskHasUserId(int userId) {
        return (root, query, builder) -> {
            Join<TaskMaster, User> taskMasterProjectJoin = root.join(Task_.taskMaster).join(TaskMaster_.user);
            return builder.equal(taskMasterProjectJoin.get(User_.USER_ID), userId);
        };
    }

    public static class TaskHasProject implements Specification<Task> {
        private int projectId;

        public TaskHasProject(int projectId) {
            this.projectId = projectId;
        }

        @Override
        public Predicate toPredicate(Root<Task> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
            Join<TaskMaster, Project> taskMasterProjectJoin = root.join(Task_.taskMaster).join(TaskMaster_.project);
            return builder.equal(taskMasterProjectJoin.get(Project_.ID), projectId);
        }
    }

    public static class TaskHasListOfProject implements Specification<Task> {
        private List<Integer> listOfProjectIds;

        public TaskHasListOfProject(List<Integer> listOfProjectIds) {
            this.listOfProjectIds = listOfProjectIds;
        }

        @Override
        public Predicate toPredicate(Root<Task> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
            Join<TaskMaster, Project> taskMasterProjectJoin = root.join(Task_.taskMaster).join(TaskMaster_.project);
            return taskMasterProjectJoin.get(Project_.ID).in(this.listOfProjectIds);
        }
    }

    public static class TaskHasUser implements Specification<Task> {
        private int userId;

        public TaskHasUser(int userId) {
            this.userId = userId;
        }

        @Override
        public Predicate toPredicate(Root<Task> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
            Join<TaskMaster, User> taskMasterProjectJoin = root.join(Task_.taskMaster).join(TaskMaster_.user);
            return builder.equal(taskMasterProjectJoin.get(User_.USER_ID), userId);
        }
    }

    public static class TaskBetweenDate implements Specification<Task> {
        private LocalDateTime fromDate;
        private LocalDateTime toDate;

        public TaskBetweenDate(LocalDateTime fromDate, LocalDateTime toDate) {
            this.fromDate = fromDate;
            this.toDate = toDate;
        }

        @Override
        public Predicate toPredicate(Root<Task> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
            return builder.between(root.<LocalDateTime>get(Task_.createdOn), fromDate, toDate);
        }
    }
}