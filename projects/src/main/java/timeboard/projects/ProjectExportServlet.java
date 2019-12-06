package timeboard.projects;

/*-
 * #%L
 * projects
 * %%
 * Copyright (C) 2019 Timeboard
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

import org.springframework.beans.factory.annotation.Autowired;
import timeboard.core.api.ProjectExportService;
import timeboard.core.api.ProjectService;
import timeboard.core.api.exceptions.BusinessException;
import timeboard.core.model.Project;
import timeboard.core.model.User;
import timeboard.core.ui.TimeboardServlet;
import timeboard.core.ui.ViewModel;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;



@WebServlet(name = "ProjectExportServlet", urlPatterns = "/projects/export")
public class ProjectExportServlet  extends TimeboardServlet {

    @Autowired
    private ProjectService projectService;

    @Autowired(
            required = false
    )
    private List<ProjectExportService> projectExportServices;

    @Override
    protected ClassLoader getTemplateResolutionClassLoader() {
        return ProjectExportServlet.class.getClassLoader();
    }


    @Override
    protected void handleGet(User actor, HttpServletRequest req, HttpServletResponse resp, ViewModel viewModel) throws ServletException, IOException, BusinessException {

        final String type = req.getParameter("type");
        final long projectID = Long.parseLong(req.getParameter("projectID"));

        final Project project = this.projectService.getProjectByID(actor, projectID);
        project.setTasks(new HashSet<>(this.projectService.listProjectTasks(actor, project)));

        final Optional<ProjectExportService> optionalService = this.projectExportServices.stream()
                .filter(projectExportService -> projectExportService.isCandidate(type))
                .findFirst();

        if (optionalService.isPresent()) {
            try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                optionalService.get().export(actor, project.getId(), buf);
                resp.setContentLengthLong(buf.toByteArray().length);
                resp.setHeader("Expires:", "0");
                resp.setHeader("Content-Disposition", "attachment; filename=" + project.getName() + "." + optionalService.get().getExtension());
                resp.setContentType(optionalService.get().getMimeType());

                resp.getOutputStream().write(buf.toByteArray());
                resp.getOutputStream().flush();

                resp.setStatus(201);
            }
        } else {
            resp.setStatus(404);
        }
    }


}
