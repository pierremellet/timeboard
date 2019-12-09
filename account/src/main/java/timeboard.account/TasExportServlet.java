package timeboard.account;

/*-
 * #%L
 * webui
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import timeboard.core.api.ProjectImportService;
import timeboard.core.api.ProjectService;
import timeboard.core.model.Project;
import timeboard.core.model.TASData;
import timeboard.core.model.User;
import timeboard.core.ui.TimeboardServlet;
import timeboard.core.ui.ViewModel;

@WebServlet(name = "TasExportServlet", urlPatterns = "/account/exportTAS")
//TIME ATTACHMENT SHEET
public class TasExportServlet extends TimeboardServlet {

    @Autowired
    private ProjectService projectService;

    @Autowired(
            required = false
    )
    private List<ProjectImportService> projectImportServlets;

    @Override
    protected ClassLoader getTemplateResolutionClassLoader() {
        return TasExportServlet.class.getClassLoader();
    }

    @Override
    protected void handlePost(User actor, HttpServletRequest request, HttpServletResponse response, ViewModel viewModel) throws ServletException, IOException {

        try {

            int month = Integer.parseInt(request.getParameter("month"));
            int year = Integer.parseInt(request.getParameter("year"));
            Long projectID = Long.parseLong(request.getParameter("projectID"));

            Calendar cal = Calendar.getInstance();
            cal.set(year, month-1, 1, 2, 0);

            try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                final String mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                InputStream inputStream = TasExportServlet.class.getClassLoader().getResourceAsStream("/resources/templates/template-TAS_fr.xls");



                String filename = "TAS_"+year+"_"+month+"_"+actor.getScreenName().replaceAll("'| |", "")+"_"+new Date().getTime();
                String sheetName = "TAS_"+year+"_"+month+"_"+actor.getScreenName().replaceAll("'| |", "");
                response.setContentLengthLong(buf.toByteArray().length);
                response.setHeader("Expires:", "0");
                response.setHeader("Content-Disposition", "attachment; filename=" + filename + ".xls");

                Project project = projectService.getProjectByID(actor, projectID);

                TASData data = projectService.generateTasData(actor, project, month, year);

                ExcelTASReport tasReport = new ExcelTASReport(response.getOutputStream());


                response.setContentType(mimeType);
                //response.getOutputStream().write(buf.toByteArray());
                response.getOutputStream().flush();

            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        response.sendRedirect("/account");


    }


}

