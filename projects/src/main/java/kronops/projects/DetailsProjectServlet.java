package kronops.projects;

/*-
 * #%L
 * webui
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

import kronops.core.api.ProjectServiceBP;
import kronops.core.api.TreeNode;
import kronops.core.api.exceptions.BusinessException;
import kronops.core.model.Project;
import kronops.core.model.ProjectRole;
import kronops.core.ui.KronopsServlet;
import kronops.core.ui.ViewModel;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Display project details form
 * <p>
 * ex : /projects/details?id=
 */
@Component(
        service = Servlet.class,
        scope = ServiceScope.PROTOTYPE,
        property = {
                "osgi.http.whiteboard.servlet.pattern=/projects/details",
                "osgi.http.whiteboard.context.select=(osgi.http.whiteboard.context.name=kronops)"
        }
)
public class DetailsProjectServlet extends KronopsServlet {

    @Reference
    public ProjectServiceBP projectServiceBP;

    @Override
    protected ClassLoader getTemplateResolutionClassLoader() {
        return DetailsProjectServlet.class.getClassLoader();
    }



    private void prepareTemplateData(Project project, Map<String, Object> map) {
        List<TreeNode> node = this.projectServiceBP.computeClustersTree();

        final Map<Long, String> paths = new HashMap<>();
        node.forEach(treeNode -> {
            paths.putAll(treeNode.getPaths());
        });

        map.put("selected_clusters", project.getClusters().stream().map(projectCluster -> projectCluster.getId()).collect(Collectors.toList()));
        map.put("clusters", paths);
        map.put("project", project);
        map.put("members", project.getMembers());
        map.put("roles", ProjectRole.values());
        map.put("tasks", this.projectServiceBP.listProjectTasks(project));
    }


    @Override
    protected void handleGet(HttpServletRequest request, HttpServletResponse response, ViewModel viewModel) throws ServletException, IOException {
        viewModel.setTemplate("details_project.html");
        long id = Long.parseLong(request.getParameter("id"));

        Project project = this.projectServiceBP.getProject(id);


        Map<String, Object> map = new HashMap<>();
        prepareTemplateData(project, map);
        viewModel.getViewDatas().putAll(map);
    }

    @Override
    protected void handlePost(HttpServletRequest request, HttpServletResponse response, ViewModel viewModel) throws ServletException, IOException {
        viewModel.setTemplate("details_project.html");
        Map<String, Object> map = new HashMap<>();

        //Extract project
        long id = Long.parseLong(request.getParameter("id"));
        Project project = this.projectServiceBP.getProject(id);
        project.setName(request.getParameter("projectName"));
        project.setComments(request.getParameter("projectDescription"));

        //Extract memberships from request
        Map<Long, ProjectRole> memberships = new HashMap<>();
        Enumeration<String> params = request.getParameterNames();
        while (params.hasMoreElements()) {
            String param = params.nextElement();
            if (param.startsWith("members")) {
                String key = param.substring(param.indexOf('[') + 1, param.indexOf(']'));
                String value = request.getParameter(param);
                if (!value.isEmpty()) {
                    memberships.put(Long.parseLong(key), ProjectRole.valueOf(value));
                } else {
                    memberships.put(Long.parseLong(key), ProjectRole.CONTRIBUTOR);
                }
            }
        }

        //Extract cluster
        String[] clusterID = request.getParameterValues("cluster");
        project.getClusters().clear();
        if (clusterID != null) {
            Arrays.asList(clusterID).stream().forEach(s -> {
                project.getClusters().add(this.projectServiceBP.findProjectsClusterByID(Long.parseLong(s)));
            });
        }

        try {
            this.projectServiceBP.updateProject(project, memberships);
        } catch (BusinessException e) {
            map.put("error", e.getLocalizedMessage());
        }

        prepareTemplateData(project, map);

        viewModel.getViewDatas().putAll(map);
    }
}