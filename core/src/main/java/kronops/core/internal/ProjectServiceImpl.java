package kronops.core.internal;

/*-
 * #%L
 * core
 * %%
 * Copyright (C) 2019 Kronops
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import kronops.core.api.ProjectService;
import kronops.core.api.ProjectTasks;
import kronops.core.api.TreeNode;
import kronops.core.api.UserService;
import kronops.core.api.exceptions.BusinessException;
import kronops.core.internal.rules.ActorIsProjectMember;
import kronops.core.internal.rules.Rule;
import kronops.core.internal.rules.RuleSet;
import kronops.core.internal.rules.TaskHasNoImputation;
import kronops.core.model.*;
import org.apache.aries.jpa.template.JpaTemplate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceScope;
import org.osgi.service.log.LogService;

import javax.persistence.EntityGraph;
import javax.persistence.TypedQuery;
import javax.transaction.TransactionManager;
import java.util.*;
import java.util.stream.Collectors;

@Component(
        service = ProjectService.class,
        immediate = true
)
public class ProjectServiceImpl implements ProjectService {

    @Reference
    private TransactionManager transactionManager;

    @Reference
    private LogService logService;

    @Reference
    private UserService userService;

    @Reference(target = "(osgi.unit.name=kronops-pu)", scope = ReferenceScope.BUNDLE)
    private JpaTemplate jpa;


    @Override
    public void addProjectToProjectCluster(ProjectCluster projectCluster, Project newProject) {
        this.jpa.tx(entityManager -> {
            ProjectCluster c = entityManager.find(ProjectCluster.class, projectCluster.getId());
            Project p = entityManager.find(Project.class, newProject.getId());

            p.getClusters().add(c);

            entityManager.flush();
        });
    }

    @Override
    public Project createProject(User owner, String projectName) throws BusinessException {

        return this.jpa.txExpr(entityManager -> {

            Project newProject = new Project();
            newProject.setName(projectName);
            newProject.setStartDate(new Date());
            entityManager.persist(newProject);

            ProjectMembership ownerMembership = new ProjectMembership(newProject, owner, ProjectRole.OWNER);
            entityManager.persist(ownerMembership);

            this.logService.log(LogService.LOG_INFO, "Project " + projectName + " created by user " + owner.getId());

            return newProject;

        });

    }


    @Override
    public List<Project> listProjects(User user) {
        return jpa.txExpr(em -> {
            TypedQuery<Project> query = em.createQuery("select p from Project p join fetch p.members m where m.member = :user", Project.class);
            query.setParameter("user", user);
            return query.getResultList();
        });
    }

    @Override
    public Project getProject(Long projectId) {

        return jpa.txExpr(em -> {
            Project data = em.createQuery("select p from Project p where p.id = :projectID", Project.class)
                    .setParameter("projectID", projectId)
                    .getSingleResult();
            return data;
        });

    }

    @Override
    public Project deleteProjectByID(Long projectID) {
        return jpa.txExpr(em -> {
            Project p = em.find(Project.class, projectID);
            em.remove(p);
            em.flush();
            return p;
        });
    }

    @Override
    public Project updateProject(Project project) throws BusinessException {
        return jpa.txExpr(em -> {
            em.merge(project);
            em.flush();
            return project;
        });
    }


    @Override
    public Project updateProject(Project project, Map<Long, ProjectRole> memberships) throws BusinessException {

        return this.jpa.txExpr(entityManager -> {

            entityManager.merge(project);

            //Update existing membership
            List<Long> membershipToRemove = new ArrayList<>();

            List<Long> currentMembers = project.getMembers().stream().map(pm -> pm.getMember().getId()).collect(Collectors.toList());
            List<Long> membershipToAdd = memberships.keySet().stream().filter(mID -> currentMembers.contains(mID) == false).collect(Collectors.toList());


            project.getMembers().forEach(projectMembership -> {
                if (memberships.containsKey(projectMembership.getMember().getId())) {
                    // Update existing user membership role
                    projectMembership.setRole(memberships.get(projectMembership.getMember().getId()));
                } else {
                    // Store user to removed
                    membershipToRemove.add(projectMembership.getMember().getId());
                }
            });

            //Remove membership
            project.getMembers().removeIf(projectMembership -> membershipToRemove.contains(projectMembership.getMember().getId()));

            //Add new membership
            membershipToAdd.forEach((aLong) -> {
                ProjectMembership projectMembership = new ProjectMembership();
                projectMembership.setProject(project);
                projectMembership.setRole(memberships.get(aLong));
                projectMembership.setMember(this.userService.findUserByID(aLong));
                entityManager.persist(projectMembership);
                project.getMembers().add(projectMembership);
            });

            entityManager.flush();


            return project;
        });

    }

    @Override
    public void save(ProjectMembership projectMembership) {
        this.jpa.tx(entityManager -> {
            entityManager.persist(projectMembership);
        });
    }

    @Override
    public void deleteProjectClusterByID(Long clusterID) {
        this.jpa.tx(entityManager -> {
            ProjectCluster pc = entityManager.find(ProjectCluster.class, clusterID);
            entityManager.remove(pc);
            entityManager.flush();
        });
    }

    @Override
    public void updateProjectClusters(List<ProjectCluster> updatedProjectCluster, Map<Long, Long> clusterParent) {

        this.jpa.tx(entityManager -> {
            updatedProjectCluster.forEach(projectCluster -> {
                if (clusterParent.containsKey(projectCluster.getId())) {
                    ProjectCluster parentCluster = entityManager.find(ProjectCluster.class, clusterParent.get(projectCluster.getId()));
                    if (parentCluster != null) {
                        projectCluster.setParent(parentCluster);
                    }
                }
                entityManager.merge(projectCluster);
            });
            entityManager.flush();
        });

    }

    @Override
    public List<Task> listProjectTasks(Project project) {
        return this.jpa.txExpr(entityManager -> {
            TypedQuery<Task> q = entityManager.createQuery("select t from Task t where t.project = :project", Task.class);
            q.setParameter("project", project);
            return q.getResultList();
        });
    }

    @Override
    public List<Task> listUserTasks(User user){
        return this.jpa.txExpr(entityManager -> {
            TypedQuery<Task> q = entityManager.createQuery("select t from Task t where t.assigned = :user", Task.class);
            q.setParameter("user", user);
            return q.getResultList();
        });
    }


    @Override
    public Task createTask(Project project, Task task) {
        return this.jpa.txExpr(entityManager -> {
            entityManager.persist(task);
            entityManager.merge(project);
            task.setProject(project);
            entityManager.flush();
            return task;
        });
    }

    @Override
    public Task updateTask(Task task) {
        return this.jpa.txExpr(entityManager -> {
            entityManager.merge(task);
            entityManager.flush();
            return task;
        });
    }

    @Override
    public Task getTask(long id) {
        return this.jpa.txExpr(entityManager -> {
            return entityManager.find(Task.class, id);
        });
    }


    @Override
    public Set<ProjectTasks> listTasksByProject(User actor, Date ds, Date de){
        final Set<ProjectTasks> projectTasks = new HashSet<>();

        this.jpa.tx(entityManager -> {


            TypedQuery<Task> q = entityManager
                    .createQuery("select t from Task t left join fetch t.imputations where " +
                            "t.endDate >= :ds "+
                            "and t.startDate <= :de " +
                            "and t.assigned = :actor"
                            , Task.class);
            q.setParameter("ds", ds);
            q.setParameter("de", de);
            q.setParameter("actor", actor);
            List<Task> tasks = q.getResultList();

            //rebalance task by project
            final Map<Project, Set<Task>> rebalanced = new HashMap<>();
            tasks.forEach(task -> {
                if(!rebalanced.containsKey(task.getProject())){
                    rebalanced.put(task.getProject(), new HashSet<>());
                }
                rebalanced.get(task.getProject()).add(task);
            });

            rebalanced.forEach((project, ts) -> {
                projectTasks.add(new ProjectTasks(project, ts));
            });

        });
        return projectTasks;
    }



    @Override
    public void deleteTaskByID(User actor, long taskID) throws BusinessException {

        RuleSet<Task> ruleSet = new RuleSet();
        ruleSet.addRule(new TaskHasNoImputation());
        ruleSet.addRule(new ActorIsProjectMember());

        BusinessException exp = this.jpa.txExpr(entityManager -> {
            Task task = entityManager.find(Task.class, taskID);

            Set<Rule> wrongRules = ruleSet.evaluate(actor, task);
            if (!wrongRules.isEmpty()) {
                return new BusinessException(wrongRules);
            }

            entityManager.remove(task);
            entityManager.flush();
            return null;
        });

        if (exp != null) {
            throw exp;
        }
    }


    @Override
    public ProjectCluster findProjectsClusterByID(long cluster) {
        return this.jpa.txExpr(entityManager -> entityManager.find(ProjectCluster.class, cluster));
    }

    @Override
    public void saveProjectCluster(ProjectCluster projectCluster) {
        this.jpa.tx(entityManager -> {
            entityManager.persist(projectCluster);
        });
    }

    @Override
    public List<TreeNode> computeClustersTree() {
        return this.jpa.txExpr(entityManager -> {
            TreeNode root = new TreeNode(null);
            List<ProjectCluster> projectClusters = entityManager.createQuery("select pc from ProjectCluster pc", ProjectCluster.class).getResultList();
            projectClusters.forEach(projectCluster -> {
                root.insert(projectCluster);
            });
            return root.getChildren();
        });
    }

    @Override
    public List<ProjectCluster> listProjectClusters() {
        return this.jpa.txExpr(entityManager -> {
            List<ProjectCluster> projectClusters = entityManager.createQuery("select pc from ProjectCluster pc", ProjectCluster.class).getResultList();

            return projectClusters;
        });
    }

}