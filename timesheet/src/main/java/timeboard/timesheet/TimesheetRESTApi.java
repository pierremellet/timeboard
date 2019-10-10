package timeboard.timesheet;

/*-
 * #%L
 * kanban-project-plugin
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

import com.fasterxml.jackson.databind.ObjectMapper;
import timeboard.core.api.ProjectService;
import timeboard.core.api.TimesheetService;
import timeboard.core.model.User;
import timeboard.core.ui.TimeboardServlet;
import timeboard.security.SecurityContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

@Component(
        service = Servlet.class,
        property = {
                "osgi.http.whiteboard.servlet.pattern=/timesheet/api",
                "osgi.http.whiteboard.context.select=(osgi.http.whiteboard.context.name=timeboard)"
        }

)
public class TimesheetRESTApi extends TimeboardServlet {

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private ProjectService projectService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private TimesheetService timesheetService;

    @Override
    protected ClassLoader getTemplateResolutionClassLoader() {
        return TimesheetRESTApi.class.getClassLoader();
    }


    private Date findStartDate(Calendar c, int week, int year) {
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return c.getTime();
    }

    private Date findEndDate(Calendar c, int week, int year) {
        c.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        return c.getTime();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        final List<ProjectWrapper> projects = new ArrayList<>();
        final List<ImputationWrapper> imputations = new ArrayList<>();
        final int week = Integer.parseInt(request.getParameter("week"));
        final int year = Integer.parseInt(request.getParameter("year"));
        boolean validated = false;

        final Calendar c = Calendar.getInstance();
        c.set(Calendar.WEEK_OF_YEAR, week);
        c.set(Calendar.YEAR, year);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.HOUR_OF_DAY, 2);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        final Date ds = findStartDate(c, week, year);
        final Date de = findEndDate(c, week, year);

        // Create days for current week
        final List<DateWrapper> days = new ArrayList<>();
        c.setTime(ds); //reset calendar to start date
        for (int i = 0; i < 7; i++) {
            DateWrapper dw = new DateWrapper(
                    c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH),
                    c.getTime()
            );
            days.add(dw);
            c.add(Calendar.DAY_OF_YEAR, 1);
        }

        //Get tasks for current week
        if (this.projectService != null) {
            User actor = SecurityContext.getCurrentUser(request);
            this.projectService.listTasksByProject(actor, ds, de).stream().forEach(projectTasks -> {
                List<TaskWrapper> tasks = new ArrayList<>();

                projectTasks.getTaskRevisions().stream().forEach(task -> {
                    tasks.add(new TaskWrapper(
                            task.getId(),
                            task.getLatestRevision().getName(),
                            task.getEffortSpent(),
                            task.getRemainsToBeDone(),
                            task.getEstimateWork(),
                            task.getReEstimateWork(),
                            task.getLatestRevision().getStartDate(),
                            task.getLatestRevision().getEndDate()));

                    days.forEach(dateWrapper -> {
                        double i = task.findTaskImputationValueByDate(dateWrapper.date);
                        imputations.add(new ImputationWrapper(task.getId(), i, dateWrapper.date));
                    });
                });

                projects.add(new ProjectWrapper(
                        projectTasks.getProject().getId(),
                        projectTasks.getProject().getName(),
                        tasks));
            });
        }

        if (this.timesheetService != null) {
            validated = this.timesheetService.isTimesheetValidated(SecurityContext.getCurrentUser(request), year, week);
        }


        Timesheet ts = new Timesheet(validated, year, week, days, projects, imputations);

        response.setContentType("application/json");
        MAPPER.writeValue(response.getWriter(), ts);
    }


    public static class Timesheet {

        private final boolean validated;
        private final int year;
        private final int week;
        private final List<DateWrapper> days;
        private final List<ProjectWrapper> projects;
        private final List<ImputationWrapper> imputations;

        public Timesheet(boolean validated, int year, int week, List<DateWrapper> days, List<ProjectWrapper> projects, List<ImputationWrapper> imputationWrappers) {
            this.validated = validated;
            this.year = year;
            this.week = week;
            this.days = days;
            this.projects = projects;
            this.imputations = imputationWrappers;
        }

        public boolean isValidated() {
            return validated;
        }

        public int getYear() {
            return year;
        }

        public int getWeek() {
            return week;
        }

        public List<DateWrapper> getDays() {
            return days;
        }

        public Map<Long, ProjectWrapper> getProjects() {
            final Map<Long, ProjectWrapper> res = new HashMap<>();
            this.projects.forEach(projectWrapper -> {
                res.put(projectWrapper.getProjectID(), projectWrapper);
            });
            return res;
        }

        public Map<String, Map<Long, Double>> getImputations() {

            final Map<String, Map<Long, Double>> res = new HashMap<>();

            this.imputations.stream().forEach(d -> {
                String date = DATE_FORMAT.format(d.date);
                if (res.get(date) == null) {
                    res.put(date, new HashMap<>());
                }
                res.get(date).put(d.taskID, d.value);
            });

            return res;

        }
    }


    public static class DateWrapper {

        private final String day;
        private final Date date;

        public DateWrapper(String day, Date date) {
            this.day = day;
            this.date = date;
        }

        public String getDay() {
            return day;
        }

        public String getDate() {
            return DATE_FORMAT.format(date);
        }
    }

    public static class ProjectWrapper {

        private final long projectID;
        private final String projectName;
        private final List<TaskWrapper> tasks;

        public ProjectWrapper(long projectID, String projectName, List<TaskWrapper> tasks) {
            this.projectID = projectID;
            this.projectName = projectName;
            this.tasks = tasks;
        }

        public long getProjectID() {
            return projectID;
        }

        public String getProjectName() {
            return projectName;
        }

        public Map<Long, TaskWrapper> getTasks() {
            final Map<Long, TaskWrapper> res = new HashMap<>();

            this.tasks.forEach(taskWrapper -> {
                res.put(taskWrapper.getTaskID(), taskWrapper);
            });

            return res;
        }
    }

    public static class TaskWrapper {
        private final long taskID;
        private final String taskName;
        private final double effortSpent;
        private final double remainToBeDone;
        private final double reEstimateWork;
        private final double estimateWork;
        private final Date startDate;
        private final Date endDate;

        public TaskWrapper(long taskID, String taskName, double effortSpent, double remainToBeDone, double estimateWork, double reEstimateWork, Date startDate, Date endDate) {
            this.taskID = taskID;
            this.taskName = taskName;
            this.effortSpent = effortSpent;
            this.remainToBeDone = remainToBeDone;
            this.estimateWork = estimateWork;
            this.reEstimateWork = reEstimateWork;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public String getStartDate() {
            return DATE_FORMAT.format(startDate);
        }

        public String getEndDate() {
            return DATE_FORMAT.format(endDate);
        }

        public double getRemainToBeDone() {
            return remainToBeDone;
        }

        public double getReEstimateWork() {
            return reEstimateWork;
        }

        public double getEstimateWork() {
            return estimateWork;
        }

        public double getEffortSpent() {
            return effortSpent;
        }

        public long getTaskID() {
            return taskID;
        }

        public String getTaskName() {
            return taskName;
        }
    }

    public static class ImputationWrapper {
        private final long taskID;
        private final double value;
        private final Date date;

        public ImputationWrapper(long taskID, double value, Date date) {
            this.taskID = taskID;
            this.value = value;
            this.date = date;
        }

        public long getTaskID() {
            return taskID;
        }

        public double getValue() {
            return value;
        }

        public Date getDate() {
            return date;
        }
    }
}