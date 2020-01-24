package timeboard.reports;

/*-
 * #%L
 * reports
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import timeboard.core.api.ReportService;
import timeboard.core.api.ThreadLocalStorage;
import timeboard.core.api.UserService;
import timeboard.core.model.Account;
import timeboard.core.model.Report;
import timeboard.core.security.TimeboardAuthentication;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/reports")
public class ReportsController {

    @Autowired
    private ReportService reportService;

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private List<ReportController> reportControllers;

    @GetMapping
    protected String handleGet() {
        return "reports.html";
    }


    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    protected ResponseEntity<List<ReportDecorator>> reportList(final TimeboardAuthentication authentication, final Model model) {
        final Account actor = authentication.getDetails();
        final List<ReportDecorator> reports = this.reportService.listReports(actor)
                .stream()
                .map(report -> new ReportDecorator(report))
                .collect(Collectors.toList());
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/create")
    protected String createReport(final Model model) throws ServletException, IOException {
        model.addAttribute("allReportTypes", this.reportControllers);
        model.addAttribute("report", new Report());
        model.addAttribute("action", "create");


        return "create_report.html";
    }

    @PostMapping("/create")
    protected String handlePost(final TimeboardAuthentication authentication,
                                @ModelAttribute final Report report, final RedirectAttributes attributes) {

        final Account actor = authentication.getDetails();
        final Long organizationID = ThreadLocalStorage.getCurrentOrgId();
        final Account organization = userService.findUserByID(organizationID);

        final String projectFilter = report.getFilterProject();

        this.reportService.createReport(
                actor,
                report.getName(),
                organization,
                report.getType(),
                projectFilter
        );
        attributes.addFlashAttribute("success", "Report created successfully.");

        return "redirect:/reports";
    }

    @GetMapping("/delete/{reportID}")
    protected String deleteReport(final TimeboardAuthentication authentication,
                                  @PathVariable final long reportID,
                                  final RedirectAttributes attributes) {

        this.reportService.deleteReportByID(authentication.getDetails(), reportID);

        attributes.addFlashAttribute("success", "Report deleted successfully.");

        return "redirect:/reports";
    }

    @GetMapping("/edit/{reportID}")
    protected String editReport(final TimeboardAuthentication authentication,
                                @PathVariable final long reportID, final Model model) {
        model.addAttribute("allReportTypes", this.reportControllers);
        model.addAttribute("reportID", reportID);
        model.addAttribute("action", "edit");
        model.addAttribute("report", this.reportService.getReportByID(authentication.getDetails(), reportID));
        return "create_report.html";
    }

    @PostMapping("/edit/{reportID}")
    protected String handlePost(final TimeboardAuthentication authentication,
                                @PathVariable final long reportID,
                                @ModelAttribute final Report report, final RedirectAttributes attributes) {

        final Account actor = authentication.getDetails();
        final Long organizationID = ThreadLocalStorage.getCurrentOrgId();
        final Account organization = userService.findUserByID(organizationID);

        final Report updatedReport = this.reportService.getReportByID(organization, reportID);
        updatedReport.setName(report.getName());
        updatedReport.setType(report.getType());
        updatedReport.setFilterProject(report.getFilterProject());

        this.reportService.updateReport(actor, updatedReport);
        attributes.addFlashAttribute("success", "Report updated successfully.");

        return "redirect:/reports";
    }

    @PostMapping(value = "/refreshProjectSelection",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity refreshProjectSelection(
            final TimeboardAuthentication authentication,
            @RequestBody final MultiValueMap<String, String> filterProjectsMap) {

        final Account actor = authentication.getDetails();

        final String filterProjects = filterProjectsMap.getFirst("filter");

        // If there is no filter, don't display all the projects
        if (filterProjects == null || filterProjects.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Impossible to display the projects. Give a filter.");
        }

        final String[] filters = filterProjects.split("\n");
        final List<ReportService.ProjectWrapper> projects = this.reportService
                .findProjects(actor, authentication.getCurrentOrganization(), Arrays.asList(filters));

        return ResponseEntity.ok(projects);
    }

    @GetMapping("/view/{reportID}")
    public ModelAndView viewReport(
            final TimeboardAuthentication authentication,
            @PathVariable final long reportID) {

        final ModelAndView mav = new ModelAndView();

        final Report report = this.reportService.getReportByID(authentication.getDetails(), reportID);

        final Optional<ReportController> reportController = this.reportControllers.stream()
                .filter(rc -> rc.reportID().equals(report.getType())).findFirst();

        if(reportController.isPresent()){
            mav.getModel().put("fragment", reportController.get().reportView());
        }

        mav.getModel().put("report", report);
        mav.getModel().put("reportController", reportController.get());
        mav.setViewName("report_layout.html");

        return mav;
    }

    @GetMapping("/view/{reportID}/data")
    protected ResponseEntity viewReportData(
            final TimeboardAuthentication authentication,
            @PathVariable final long reportID) {

        final Report report = this.reportService.getReportByID(authentication.getDetails(), reportID);
        final Optional<ReportController> reportController = this.reportControllers
                .stream()
                .filter(rc -> rc.reportID().equals(report.getType()))
                .findFirst();

        if(reportController.isPresent()){
            final Model model = reportController.get().getReportModel(authentication, report);
            return ResponseEntity.ok(model);
        }

        return ResponseEntity.badRequest().build();
    }


    private static class ReportDecorator {

        private final Report report;

        public ReportDecorator(final Report report) {
            this.report = report;
        }

        public long getID() {
            return this.report.getId();
        }

        public String getName() {
            return this.report.getName();
        }

    }

}
