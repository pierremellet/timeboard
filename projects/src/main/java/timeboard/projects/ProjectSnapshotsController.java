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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import timeboard.core.TimeboardAuthentication;
import timeboard.core.api.ProjectDashboard;
import timeboard.core.api.ProjectService;
import timeboard.core.api.ProjectSnapshotService;
import timeboard.core.api.exceptions.BusinessException;
import timeboard.core.model.Account;
import timeboard.core.model.Project;
import timeboard.core.model.ProjectSnapshot;
import timeboard.core.model.ValueHistory;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/projects/{projectID}/snapshots")
public class ProjectSnapshotsController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectSnapshotService projectSnapshotService;


    @GetMapping
    public String display(TimeboardAuthentication authentication, @PathVariable Long projectID, Model model) throws BusinessException {

        final Account actor = authentication.getDetails();
        final Project project = this.projectService.getProjectByID(actor, authentication.getCurrentOrganization(), projectID);

        model.addAttribute("project", project);

        return "details_project_snapshots";
    }

    @GetMapping(value = "/chart", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity getDatasForChart(TimeboardAuthentication authentication,
                                           @PathVariable Long projectID) throws BusinessException, JsonProcessingException {


        final Account actor = authentication.getDetails();
        final Project project = this.projectService.getProjectByID(actor, authentication.getCurrentOrganization(), projectID);
        final ProjectSnapshotGraphWrapper projectSnapshotGraphWrapper = this.createGraph(project.getSnapshots());
        return ResponseEntity.status(HttpStatus.OK).body(MAPPER.writeValueAsString(projectSnapshotGraphWrapper));
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ProjectSnapshotsController.ProjectSnapshotWrapper>>
    listProjectSnapshots(TimeboardAuthentication authentication,
                         @PathVariable Long projectID) throws BusinessException {

        final Account actor = authentication.getDetails();
        final Project project = this.projectService.getProjectByID(actor, authentication.getCurrentOrganization(), projectID);
        return ResponseEntity.ok(project.getSnapshots().stream().map(projectSnapshot ->
                new ProjectSnapshotsController.ProjectSnapshotWrapper(projectSnapshot)).collect(Collectors.toList()));
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ProjectSnapshotWrapper>> createSnapshot(
            @PathVariable Long projectID,
            TimeboardAuthentication authentication) throws BusinessException {

        final Account actor = authentication.getDetails();
        final Project project = this.projectService.getProjectByID(actor, authentication.getCurrentOrganization(), projectID);
        final ProjectSnapshot projectSnapshot = this.projectSnapshotService.createProjectSnapshot(actor, project);
        projectSnapshot.setProject(project);
        project.getSnapshots().add(projectSnapshot);
        this.projectService.updateProject(actor, project);
        return this.listProjectSnapshots(authentication, projectID);
    }

    @DeleteMapping(value = "/{snapshotID}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ProjectSnapshotWrapper>>
    deleteSnapshot(TimeboardAuthentication authentication,
                   @PathVariable Long projectID, @PathVariable Long snapshotID) throws BusinessException {

        final Account actor = authentication.getDetails();
        final Project project = this.projectService.getProjectByID(actor, authentication.getCurrentOrganization(), projectID);
        project.getSnapshots().removeIf(projectSnapshot -> projectSnapshot.getId().equals(snapshotID));
        this.projectService.updateProject(actor, project);
        return this.listProjectSnapshots(authentication, projectID);
    }

    public Collection<Double> quotationValuesForGraph(List<String> listOfProjectSnapshotDates, List<ProjectSnapshot> projectSnapshotList,
                                      String formatDateToDisplay, List<ProjectDashboard> projectDashboards){
        final ValueHistory[] quotationSum = {new ValueHistory(projectSnapshotList.get(0).getProjectSnapshotDate(),
                projectSnapshotList.get(0).getQuotation())};
        Map<Date, Double> quotationMap = listOfProjectSnapshotDates
                .stream()
                .map(dateString -> {
                    return formatDate(formatDateToDisplay, dateString);
                })
                .map(date -> projectDashboards.stream()
                        .filter(es -> new SimpleDateFormat(formatDateToDisplay)
                                .format(es.getDate()).equals(new SimpleDateFormat(formatDateToDisplay).format(date)))
                        .map(quotation -> {
                            quotationSum[0] = new ValueHistory(date, quotation.getQuotation());
                            return quotationSum[0];
                        })
                        .findFirst().orElse(new ValueHistory(date, quotationSum[0].getValue())))
                .collect(Collectors.toMap(
                        e -> e.getDate(),
                        e -> e.getValue(),
                        (x, y) -> y, LinkedHashMap::new
                ));
        return quotationMap.values();
    }

    public Collection<Double> originalEstimateValuesForGraph(List<String> listOfProjectSnapshotDates, List<ProjectSnapshot> projectSnapshotList,
                                                      String formatDateToDisplay, List<ProjectDashboard> projectDashboards){
        final ValueHistory[] originalEstimateSum = {new ValueHistory(projectSnapshotList.get(0).getProjectSnapshotDate(),
                projectSnapshotList.get(0).getOriginalEstimate())};
        Map<Date, Double> originalEstimateMap = listOfProjectSnapshotDates
                .stream()
                .map(dateString -> {
                    return formatDate(formatDateToDisplay, dateString);
                })
                .map(date -> projectDashboards.stream()
                        .filter(es -> new SimpleDateFormat(formatDateToDisplay)
                                .format(es.getDate()).equals(new SimpleDateFormat(formatDateToDisplay).format(date)))
                        .map(originalEstimate -> {
                            originalEstimateSum[0] = new ValueHistory(date, originalEstimate.getOriginalEstimate());
                            return originalEstimateSum[0];
                        })
                        .findFirst().orElse(new ValueHistory(date, originalEstimateSum[0].getValue())))
                .collect(Collectors.toMap(
                        e -> e.getDate(),
                        e -> e.getValue(),
                        (x, y) -> y, LinkedHashMap::new
                ));
        return originalEstimateMap.values();
    }

    public Collection<Double> realEffortValuesForGraph(List<String> listOfProjectSnapshotDates, List<ProjectSnapshot> projectSnapshotList,
                                                             String formatDateToDisplay, List<ProjectDashboard> projectDashboards){
        final ValueHistory[] realEffortSum = {new ValueHistory(projectSnapshotList.get(0).getProjectSnapshotDate(),
                projectSnapshotList.get(0).getRealEffort())};
        Map<Date, Double> realEffortMap = listOfProjectSnapshotDates
                .stream()
                .map(dateString -> {
                    return formatDate(formatDateToDisplay, dateString);
                })
                .map(date -> projectDashboards.stream()
                        .filter(es -> new SimpleDateFormat(formatDateToDisplay)
                                .format(es.getDate()).equals(new SimpleDateFormat(formatDateToDisplay).format(date)))
                        .map(realEffort -> {
                            realEffortSum[0] = new ValueHistory(date, realEffort.getRealEffort());
                            return realEffortSum[0];
                        })
                        .findFirst().orElse(new ValueHistory(date, realEffortSum[0].getValue())))
                .collect(Collectors.toMap(
                        e -> e.getDate(),
                        e -> e.getValue(),
                        (x, y) -> y, LinkedHashMap::new
                ));
        return realEffortMap.values();
    }

    public Collection<Double> effortLeftValuesForGraph(List<String> listOfProjectSnapshotDates, List<ProjectSnapshot> projectSnapshotList,
                                                       String formatDateToDisplay, List<ProjectDashboard> projectDashboards){
        final ValueHistory[] effortLeftSum = {new ValueHistory(projectSnapshotList.get(0).getProjectSnapshotDate(),
                projectSnapshotList.get(0).getEffortLeft())};
        Map<Date, Double> effortLeftMap = listOfProjectSnapshotDates
                .stream()
                .map(dateString -> {
                    return formatDate(formatDateToDisplay, dateString);
                })
                .map(date -> projectDashboards.stream()
                        .filter(es -> new SimpleDateFormat(formatDateToDisplay)
                                .format(es.getDate()).equals(new SimpleDateFormat(formatDateToDisplay).format(date)))
                        .map(effortLeft -> {
                            effortLeftSum[0] = new ValueHistory(date, effortLeft.getEffortLeft());
                            return effortLeftSum[0];
                        })
                        .findFirst().orElse(new ValueHistory(date, effortLeftSum[0].getValue())))
                .collect(Collectors.toMap(
                        e -> e.getDate(),
                        e -> e.getValue(),
                        (x, y) -> y, LinkedHashMap::new
                ));
        return effortLeftMap.values();
    }

    public Collection<Double> effortSpentValuesForGraph(List<String> listOfProjectSnapshotDates, List<ProjectSnapshot> projectSnapshotList,
                                                       String formatDateToDisplay, List<ProjectDashboard> projectDashboards){
        final ValueHistory[] effortSpentSum = {new ValueHistory(projectSnapshotList.get(0).getProjectSnapshotDate(),
                projectSnapshotList.get(0).getEffortSpent())};
        Map<Date, Double> effortSpentMap = listOfProjectSnapshotDates
                .stream()
                .map(dateString -> {
                    return formatDate(formatDateToDisplay, dateString);
                })
                .map(date -> projectDashboards.stream()
                        .filter(es -> new SimpleDateFormat(formatDateToDisplay)
                                .format(es.getDate()).equals(new SimpleDateFormat(formatDateToDisplay).format(date)))
                        .map(effortSpent -> {
                            effortSpentSum[0] = new ValueHistory(date, effortSpent.getEffortSpent());
                            return effortSpentSum[0];
                        })
                        .findFirst().orElse(new ValueHistory(date, effortSpentSum[0].getValue())))
                .collect(Collectors.toMap(
                        e -> e.getDate(),
                        e -> e.getValue(),
                        (x, y) -> y, LinkedHashMap::new
                ));
        return effortSpentMap.values();
    }

    public ProjectSnapshotGraphWrapper createGraph(List<ProjectSnapshot> projectSnapshotList){

        ProjectSnapshotsController.ProjectSnapshotGraphWrapper wrapper = new ProjectSnapshotsController.ProjectSnapshotGraphWrapper();

        final String formatDateToDisplay = "yyyy-MM-dd HH:mm:ss.S";
        final List<String> listOfProjectSnapshotDates = new ArrayList<>();
        final List<ProjectDashboard> projectDashboards = new ArrayList<>();
        projectSnapshotList.forEach(snapshot -> {
            listOfProjectSnapshotDates.add(String.format(snapshot.getProjectSnapshotDate().toString(), formatDateToDisplay));
            projectDashboards.add(new ProjectDashboard(snapshot.getQuotation(),
                    snapshot.getOriginalEstimate(), snapshot.getEffortLeft(), snapshot.getEffortSpent(),
                    snapshot.getProjectSnapshotDate()));
        });
        wrapper.setListOfProjectSnapshotDates(listOfProjectSnapshotDates);

        wrapper.setQuotationData(this.quotationValuesForGraph(listOfProjectSnapshotDates, projectSnapshotList,
                formatDateToDisplay, projectDashboards));

        wrapper.setOriginalEstimateData(this.originalEstimateValuesForGraph(listOfProjectSnapshotDates, projectSnapshotList,
                formatDateToDisplay, projectDashboards));

        wrapper.setRealEffortData(this.realEffortValuesForGraph(listOfProjectSnapshotDates, projectSnapshotList,
                formatDateToDisplay, projectDashboards));

        wrapper.setEffortLeftData(this.effortLeftValuesForGraph(listOfProjectSnapshotDates, projectSnapshotList,
                formatDateToDisplay, projectDashboards));

        wrapper.setEffortSpentData(this.effortSpentValuesForGraph(listOfProjectSnapshotDates, projectSnapshotList,
                formatDateToDisplay, projectDashboards));

        return wrapper;
    }

    private Date formatDate(String formatDateToDisplay, String dateString) {
        try {
            return new SimpleDateFormat(formatDateToDisplay).parse(dateString);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static class ProjectSnapshotWrapper {

        private double quotation;
        private double realEffort;
        private double effortSpent;
        private double effortLeft;
        private double originalEstimate;
        private Date projectSnapshotDate;
        private Long id;

        public ProjectSnapshotWrapper(ProjectSnapshot projectSnapshot) {
            this.projectSnapshotDate = projectSnapshot.getProjectSnapshotDate();
            this.originalEstimate = projectSnapshot.getOriginalEstimate();
            this.effortLeft = projectSnapshot.getEffortLeft();
            this.effortSpent = projectSnapshot.getEffortSpent();
            this.realEffort = projectSnapshot.getRealEffort();
            this.quotation = projectSnapshot.getQuotation();
            this.id = projectSnapshot.getId();
        }

        public double getQuotation() { return quotation; }

        public double getRealEffort() { return realEffort; }

        public double getEffortSpent() { return effortSpent; }

        public double getEffortLeft() { return effortLeft; }

        public double getOriginalEstimate() { return originalEstimate; }

        public Date getProjectSnapshotDate() { return projectSnapshotDate; }

        public Long getId() { return id; }

        public void setQuotation(double quotation) { this.quotation = quotation; }

        public void setRealEffort(double realEffort) { this.realEffort = realEffort; }

        public void setEffortSpent(double effortSpent) { this.effortSpent = effortSpent; }

        public void setEffortLeft(double effortLeft) { this.effortLeft = effortLeft; }

        public void setOriginalEstimate(double originalEstimate) { this.originalEstimate = originalEstimate; }

        public void setProjectSnapshotDate(Date projectSnapshotDate) { this.projectSnapshotDate = projectSnapshotDate; }

        public void setId(Long id) { this.id = id; }
    }

    public static class ProjectSnapshotGraphWrapper implements Serializable {
        public List<String> listOfProjectSnapshotDates;
        public Collection<Double> quotationData;
        public Collection<Double> originalEstimateData;
        public Collection<Double> realEffortData;
        public Collection<Double> effortSpentData;
        public Collection<Double> effortLeftData;

        public ProjectSnapshotGraphWrapper() {
        }

        public void setListOfProjectSnapshotDates(List<String> listOfProjectSnapshotDates) {
            this.listOfProjectSnapshotDates = listOfProjectSnapshotDates;
        }

        public void setQuotationData(Collection<Double> quotationData) {
            this.quotationData = quotationData;

        }

        public void setRealEffortData(Collection<Double> realEffortData) {
            this.realEffortData = realEffortData;
        }

        public void setOriginalEstimateData(Collection<Double> originalEstimateData) {
            this.originalEstimateData = originalEstimateData;
        }

        public void setEffortSpentData(Collection<Double> effortSpentData) {
            this.effortSpentData = effortSpentData;
        }

        public void setEffortLeftData(Collection<Double> effortLeftData) {
            this.effortLeftData = effortLeftData;
        }
    }
}
