package kronops.ui.projects;

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

import kronops.core.api.KronopsServlet;
import kronops.core.api.NavigationExtPoint;
import kronops.core.api.ProjectExtPoint;
import kronops.core.api.ProjectServiceBP;
import org.osgi.service.component.annotations.*;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component(
        service = Servlet.class,
        scope = ServiceScope.PROTOTYPE,
        property = {
                "osgi.http.whiteboard.servlet.pattern=/projects/create",
                "osgi.http.whiteboard.context.select=(osgi.http.whiteboard.context.name=kronops)"
        }
)
public class CreateProjectServlet extends KronopsServlet {

    @Reference(
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    public volatile List<ProjectExtPoint> projectPlugin;

    @Reference(
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            policyOption = ReferencePolicyOption.GREEDY)
    public volatile List<NavigationExtPoint> navs;

    @Reference
    public ProjectServiceBP projectServiceBP;


    @Override
    protected String getTemplate(String path) {
        return "create_project.html";
    }

    @Override
    protected ClassLoader getTemplateResolutionClassLoader() {
        return CreateProjectServlet.class.getClassLoader();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final String projectName = request.getParameter("projectName");
        final String projectType = request.getParameter("projectType");
        this.projectServiceBP.createProject(projectName, projectType);
        response.sendRedirect("/projects");
    }

    @Override
    protected Map<String, Object> getTemplateData() {
        Map<String, Object> data = new HashMap<>();
        data.put("projects", this.projectPlugin);
        return data;
    }

    @Override
    protected List<NavigationExtPoint> getNavs() {
        return this.navs;
    }
}
