package timeboard.projects;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import timeboard.core.TimeboardAuthentication;
import timeboard.core.api.ProjectService;
import timeboard.core.api.UserService;
import timeboard.core.api.exceptions.BusinessException;
import timeboard.core.model.*;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RestController
@RequestMapping(value = "/api/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
public class TasksRestController {

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserService userService;

    @GetMapping("/batches")
    public ResponseEntity getBatches(TimeboardAuthentication authentication,
                                   HttpServletRequest request) throws JsonProcessingException {
        Account actor = authentication.getDetails();
        Project project = null;

        final String strProjectID = request.getParameter("project");
        final String strBatchType = request.getParameter("batchType");

        Long projectID = null;
        if (strProjectID != null) {
            projectID = Long.parseLong(strProjectID);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Incorrect project argument");
        }

        BatchType batchType = null;
        if (strBatchType != null) {
            batchType = BatchType.valueOf(strBatchType.toUpperCase());
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Incorrect batchType argument");
        }

        try {
            project = this.projectService.getProjectByID(actor, authentication.getCurrentOrganization(), projectID);
        } catch (BusinessException e ) {
            // just handling exception
        }
        if (project == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Project does not exists or you don't have enough permissions to access it.");
        }

        List<Batch> batchList = null;
        try {
            batchList = projectService.getBatchList(actor, project, batchType);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Project does not exists or you don't have enough permissions to access it.");
        }

        List<BatchWrapper> batchWrapperList = new ArrayList<>();
        batchList.forEach(batch -> batchWrapperList.add(new BatchWrapper(batch.getId(), batch.getName())));

        return ResponseEntity.status(HttpStatus.OK).body(MAPPER.writeValueAsString(batchWrapperList.toArray()));
    }

    @GetMapping
    public ResponseEntity getTasks(TimeboardAuthentication authentication,
                                   HttpServletRequest request) throws JsonProcessingException {

        Account actor = authentication.getDetails();

        final String strProjectID = request.getParameter("project");
        Long projectID = null;
        if (strProjectID != null) {
            projectID = Long.parseLong(strProjectID);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Incorrect project argument");
        }

        try {
            final Project project = this.projectService.getProjectByID(actor, authentication.getCurrentOrganization(), projectID);
            if (project == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Project does not exists or you don't have enough permissions to access it.");
            }
            final List<Task> tasks = this.projectService.listProjectTasks(actor, project);

            final List<TaskWrapper> result = new ArrayList<>();

            for (Task task : tasks) {
                Account assignee = task.getAssigned();
                if (assignee == null) {
                    assignee = new Account();
                    assignee.setId(0);
                    assignee.setName("");
                    assignee.setFirstName("");
                }

                List<Long> batchIDs = new ArrayList<>();
                List<String> batchNames = new ArrayList<>();

                task.getBatches().stream().forEach(b -> {
                    batchIDs.add(b.getId());
                    batchNames.add(b.getName());
                });
                result.add(new TaskWrapper(
                        task.getId(),
                        task.getName(),
                        task.getComments(),
                        task.getOriginalEstimate(),
                        task.getStartDate(),
                        task.getEndDate(),
                        assignee.getScreenName(), assignee.getId(),
                        task.getTaskStatus().name(),
                        (task.getTaskType() != null ? task.getTaskType().getId() : 0L),
                        batchIDs, batchNames,
                        task.getTaskStatus().getLabel(),
                        (task.getTaskType() != null ? task.getTaskType().getTypeName() : "")
                ));

            }
            return ResponseEntity.status(HttpStatus.OK).body(MAPPER.writeValueAsString(result.toArray()));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }

    }

    @GetMapping("/chart")
    public ResponseEntity getDatasForCharts(
            TimeboardAuthentication authentication,
            HttpServletRequest request) throws BusinessException, JsonProcessingException {

        TaskGraphWrapper wrapper = new TaskGraphWrapper();
        Account actor = authentication.getDetails();

        final String taskIdStr = request.getParameter("task");
        Long taskID = null;
        if (taskIdStr != null) {
            taskID = Long.parseLong(taskIdStr);
        }
        if (taskID == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid argument taskId.");
        }

        final Task task = (Task) this.projectService.getTaskByID(actor, taskID);

        // Datas for dates (Axis X)
        final String formatLocalDate = "yyyy-MM-dd";
        final String formatDateToDisplay = "dd/MM/yyyy";
        final LocalDate start = LocalDate.parse(new SimpleDateFormat(formatLocalDate).format(task.getStartDate()));
        final LocalDate end = LocalDate.parse(new SimpleDateFormat(formatLocalDate).format(task.getEndDate()));
        final List<String> listOfTaskDates = start.datesUntil(end.plusDays(1))
                .map(localDate -> localDate.format(DateTimeFormatter.ofPattern(formatDateToDisplay)))
                .collect(Collectors.toList());
        wrapper.setListOfTaskDates(listOfTaskDates);

        // Datas for effort spent (Axis Y)
        List<ValueHistory> effortSpentDB = this.projectService.getEffortSpentByTaskAndPeriod(actor, task, task.getStartDate(), task.getEndDate());
        final ValueHistory[] lastEffortSpentSum = {new ValueHistory(task.getStartDate(), 0.0)};
        Map<Date, Double> effortSpentMap = listOfTaskDates
                .stream()
                .map(dateString -> {
                    return formatDate(formatDateToDisplay, dateString);
                })
                .map(date -> effortSpentDB.stream()
                        .filter(es -> new SimpleDateFormat(formatDateToDisplay)
                                .format(es.getDate()).equals(new SimpleDateFormat(formatDateToDisplay).format(date)))
                        .map(effort -> {
                            lastEffortSpentSum[0] = new ValueHistory(date, effort.getValue());
                            return lastEffortSpentSum[0];
                        })
                        .findFirst().orElse(new ValueHistory(date, lastEffortSpentSum[0].getValue())))
                .collect(Collectors.toMap(
                        e -> e.getDate(),
                        e -> e.getValue(),
                        (x, y) -> y, LinkedHashMap::new
                ));
        wrapper.setEffortSpentData(effortSpentMap.values());

        return ResponseEntity.status(HttpStatus.OK).body(MAPPER.writeValueAsString(wrapper));

    }

    private Date formatDate(String formatDateToDisplay, String dateString) {
        try {
            return new SimpleDateFormat(formatDateToDisplay).parse(dateString);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }


    @GetMapping("/approve")
    public ResponseEntity approveTask(TimeboardAuthentication authentication, HttpServletRequest request) {
        Account actor = authentication.getDetails();
        return this.changeTaskStatus(actor, request, TaskStatus.IN_PROGRESS);
    }

    @GetMapping("/deny")
    public ResponseEntity denyTask(TimeboardAuthentication authentication, HttpServletRequest request) {
        Account actor = authentication.getDetails();
        return this.changeTaskStatus(actor, request, TaskStatus.REFUSED);
    }

    private ResponseEntity changeTaskStatus(Account actor, HttpServletRequest request, TaskStatus status) {
        final String taskIdStr = request.getParameter("task");
        Long taskID = null;
        if (taskIdStr != null) {
            taskID = Long.parseLong(taskIdStr);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing argument taskId.");
        }
        if (taskID == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid argument taskId.");
        }

        Task task;
        try {
            task = (Task) this.projectService.getTaskByID(actor, taskID);
            task.setTaskStatus(status);
            this.projectService.updateTask(actor, task);
        } catch (ClassCastException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Task is not a project task.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Task id not found.");
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @GetMapping("/delete")
    public ResponseEntity deleteTask(TimeboardAuthentication authentication,
                                     HttpServletRequest request) {
        Account actor = authentication.getDetails();

        final String taskIdStr = request.getParameter("task");
        Long taskID = null;
        if (taskIdStr != null) {
            taskID = Long.parseLong(taskIdStr);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing argument taskId.");
        }
        if (taskID == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid argument taskId.");
        }

        try {
            projectService.deleteTaskByID(actor, taskID);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }


    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity createTask(TimeboardAuthentication authentication,
                                     @RequestBody TaskWrapper taskWrapper) throws JsonProcessingException, BusinessException {

        Account actor = authentication.getDetails();
        Date startDate = null;
        Date endDate = null;
        try {
            startDate = DATE_FORMAT.parse(taskWrapper.startDate);
            endDate = DATE_FORMAT.parse(taskWrapper.endDate);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Incorrect date format");
        }

        if (startDate.getTime() > endDate.getTime()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Start date must be before end date ");
        }

        String name = taskWrapper.taskName;
        String comment = taskWrapper.taskComments;
        if (comment == null) {
            comment = "";
        }
        double oe = taskWrapper.originalEstimate;
        if (oe <= 0.0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Original original estimate must be positive ");
        }

        Long projectID = taskWrapper.projectID;
        Project project = null;
        try {
            project = this.projectService.getProjectByID(actor, authentication.getCurrentOrganization(), projectID);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }

        final Set<Batch> batches = getBatches(taskWrapper, actor);

        Task task = null;
        Long typeID = taskWrapper.typeID;
        Long taskID = taskWrapper.taskID;

        if (taskID != null && taskID != 0) {
            try {
                task = processUpdateTask(taskWrapper, actor, batches, taskID);

            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error in task creation please verify your inputs and retry");
            }
        } else {
            try {
                task = createTask(taskWrapper, actor, startDate, endDate, name, comment, oe, project, typeID);
            } catch (Exception e) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .header("msg", "Error in task creation please " +
                                "verify your inputs and retry. (" + e.getMessage() + ")").build();
            }
        }

        taskWrapper.setTaskID(task.getId());
        return ResponseEntity.status(HttpStatus.OK).body(MAPPER.writeValueAsString(taskWrapper));

    }

    private Task createTask(TaskWrapper taskWrapper, Account actor, Date startDate, Date endDate, String name, String comment, double oe,
                                Project project, Long typeID) {
        Account assignee = null;
        if (taskWrapper.assigneeID > 0) {
            assignee = userService.findUserByID(taskWrapper.assigneeID);
        }

        return projectService.createTask(actor, project,
                name, comment, startDate,
                endDate, oe, typeID, assignee,
                ProjectService.ORIGIN_TIMEBOARD, null, null, TaskStatus.PENDING,null);
    }

    private Set<Batch> getBatches(@RequestBody TaskWrapper taskWrapper, Account actor) throws BusinessException {
        Set<Batch> returnList = null;
        List<Long> batchIDList = taskWrapper.batchIDs;

        if(batchIDList != null && !batchIDList.isEmpty()) {
            returnList = new HashSet<>();
            for (Long batchID : batchIDList ) {
                try {
                    Batch batch = this.projectService.getBatchById(actor, batchID);
                    if (batch != null) {
                        returnList.add(batch);
                    }
                } catch (Exception e) {
                    // Do nothing, just handling the exceptions
                }
            }
        }
        return returnList;
    }

    private Task processUpdateTask(@RequestBody TaskWrapper taskWrapper,
                                   Account actor,
                                   Set<Batch> batches,
                                   Long taskID) throws BusinessException, ParseException {

        final Task task = (Task) projectService.getTaskByID(actor, taskID);

        if (taskWrapper.assigneeID != null && taskWrapper.assigneeID > 0) {
            final Account assignee = userService.findUserByID(taskWrapper.assigneeID);
            task.setAssigned(assignee);
        }
        task.setName(taskWrapper.getTaskName());
        task.setComments(taskWrapper.getTaskComments());
        task.setOriginalEstimate(taskWrapper.getOriginalEstimate());
        task.setStartDate(DATE_FORMAT.parse(taskWrapper.getStartDate()));
        task.setEndDate(DATE_FORMAT.parse(taskWrapper.getEndDate()));
        final TaskType taskType = this.projectService.findTaskTypeByID(taskWrapper.getTypeID());
        task.setTaskType(taskType);
        task.setBatches(batches);
        task.setTaskStatus(taskWrapper.getStatus() != null ? TaskStatus.valueOf(taskWrapper.getStatus()) : TaskStatus.PENDING);

        projectService.updateTask(actor, task);
        return task;
    }


    public static class TaskGraphWrapper implements Serializable {
        public List<String> listOfTaskDates;
        public Collection<Double> effortSpentData;
        public Collection<Double> realEffortData;

        public TaskGraphWrapper() {
        }

        public void setListOfTaskDates(List<String> listOfTaskDates) {
            this.listOfTaskDates = listOfTaskDates;
        }

        public void setEffortSpentData(Collection<Double> effortSpentData) {
            this.effortSpentData = effortSpentData;

        }

        public void setRealEffortData(Collection<Double> realEffortData) {
            this.realEffortData = realEffortData;
        }
    }

    public static class BatchWrapper implements Serializable {


        public Long batchID;
        public String batchName;


        public BatchWrapper(Long batchID, String batchName) {
            this.batchID = batchID;
            this.batchName = batchName;
        }

        public Long getBatchID() {
            return batchID;
        }

        public void setBatchID(Long batchID) {
            this.batchID = batchID;
        }

        public String getBatchName() {
            return batchName;
        }

        public void setBatchName(String batchName) {
            this.batchName = batchName;
        }


    }

    public static class TaskWrapper implements Serializable {
        public Long taskID;
        public Long projectID;

        public String taskName;
        public String taskComments;

        public double originalEstimate;

        public String startDate;
        public String endDate;

        public String assignee;
        public Long assigneeID;

        public Long typeID;
        public String typeName;

        public String status;
        public String statusName;

        public List<Long> batchIDs;
        public List<String> batchNames;

        public TaskWrapper() {
        }

        public TaskWrapper(Long taskID, String taskName, String taskComments, double originalEstimate,
                           Date startDate, Date endDate, String assignee, Long assigneeID,
                           String status, Long typeID, List<Long> batchIDs, List<String> batchNames, String statusName, String typeName) {

            this.taskID = taskID;

            this.taskName = taskName;
            this.taskComments = taskComments;
            this.originalEstimate = originalEstimate;
            this.startDate = DATE_FORMAT.format(startDate);
            if (endDate != null) {
                this.endDate = DATE_FORMAT.format(endDate);
            }
            this.assignee = assignee;
            this.assigneeID = assigneeID;
            this.status = status;
            this.typeID = typeID;

            this.batchNames = batchNames;
            this.batchIDs = batchIDs;

            this.statusName = statusName;
            this.typeName = typeName;
        }


        public List<Long> getBatchIDs() {
            return batchIDs;
        }

        public void setBatchIDs(List<Long> batchIDs) {
            this.batchIDs = batchIDs;
        }

        public List<String> getBatchNames() {
            return batchNames;
        }

        public void setBatchNames(List<String> batchNames) {
            this.batchNames = batchNames;
        }

        public Long getTaskID() {
            return taskID;
        }

        public void setTaskID(Long taskID) {
            this.taskID = taskID;
        }

        public String getTaskName() {
            return taskName;
        }

        public void setTaskName(String taskName) {
            this.taskName = taskName;
        }

        public String getTaskComments() {
            return taskComments;
        }

        public void setTaskComments(String taskComments) {
            this.taskComments = taskComments;
        }

        public double getOriginalEstimate() {
            return originalEstimate;
        }

        public void setOriginalEstimate(double originalEstimate) {
            this.originalEstimate = originalEstimate;
        }

        public String getStartDate() {
            return startDate;
        }

        public String getAssignee() {
            return assignee;
        }

        public void setAssignee(String assignee) {
            this.assignee = assignee;
        }

        public Long getAssigneeID() {
            return assigneeID;
        }

        public void setAssigneeID(Long assigneeID) {
            this.assigneeID = assigneeID;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getTypeID() {
            return typeID;
        }

        public void setTypeID(Long typeID) {
            this.typeID = typeID;
        }

        public Long getProjectID() {
            return projectID;
        }

        public void setProjectID(Long projectID) {
            this.projectID = projectID;
        }

        public String getEndDate() {
            return this.endDate;
        }
    }

  /*  public static class BatchWrapper {

        public Long batchID;
        public String batchName;

        public BatchWrapper(Long batchID, String batchName) {
            this.batchID = batchID;
            this.batchName = batchName;
        }

        public Long getBatchID() {
            return batchID;
        }

        public void setBatchID(Long batchID) {
            this.batchID = batchID;
        }

        public String getBatchName() {
            return batchName;
        }

        public void setBatchName(String batchName) {
            this.batchName = batchName;
        }


    }*/
}
