const currentProjectID = $("meta[property='tasks']").attr('project');
const currentBatchType = $("meta[property='tasks']").attr('batchType');

// TASK EDIT/CREATE MODAL VUEJS COMPONENT
Vue.component('task-modal', {
    template: '#task-modal-template',
    props: {
        task: Object,
        formError: String,
        modalTitle: String
    }
});

// GRAPH MODAL VUEJS COMPONENT
Vue.component('graph-modal', {
    template: '#graph-modal-template',
    props: {
        task: Object,
        formError: String,
        modalTitle: String
    }
});


// Form validations rules
const formValidationRules = {
    fields: {
        projectID: {
            identifier: 'projectID',
            rules: [ { type   : 'empty', prompt : 'Please select project'  } ]
        },
        taskName: {
            identifier: 'taskName',
            rules: [ { type   : 'empty', prompt : 'Please enter task name'  } ]
        },
        taskStartDate: {
            identifier: 'taskStartDate',
            rules: [
            { type: "empty", prompt : 'Please enter task start date'  } ]
        },
        taskEndDate: {
            identifier: 'taskEndDate',
            rules: [
            { type: "empty", prompt : 'Please enter task end date'  } ]
        },
        taskOriginalEstimate: {
            identifier: 'taskOriginalEstimate',
            rules: [ { type   : 'empty', prompt : 'Please enter task original estimate in days'  },
            { type   : 'number', prompt : 'Please enter task a number original estimate in days'  } ]
        }
    }
};

// Empty task initalisation
const emptyTask =  {
    taskID: 0,
    projectID: currentProjectID,
    taskName: "",
    taskComments: "",
    startDate: "",
    endDate:"",
    originalEstimate: 0,
    typeID: 0,
    typeName: '',
    assignee: "",
    assigneeID: 0,
    status: "PENDING",
    statusName: '',
    batchNames: [],
    batchIDs: [],
};

const projectID = $("meta[name='projectID']").attr('value');


// VUEJS MAIN APP
let app = new Vue({
    el: '#tasksList',
    data: {
        newTask: Object.assign({}, emptyTask),
        formError: "",
        batches: [],
        modalTitle: "Create task",
        table: {
            cols: [
                {
                    "slot": "name",
                    "label": "Task",
                    "sortKey": "taskName",
                    "primary" : true
                },
                {
                    "slot": "start",
                    "label": "Start date",
                    "sortKey": "taskStartDate"

                },
                {
                    "slot": "end",
                    "label": "End date",
                    "sortKey": "taskEndDate"

                },
                {
                    "slot": "oe",
                    "label": "OE",
                    "sortKey": "originalEstimate"

                },
                {
                    "slot": "assignee",
                    "label": "Assignee",
                    "sortKey": "assignee"

                },
                {
                    "slot": "status",
                    "label": "Status",
                    "sortKey": "status"

                },
                {
                    "slot": "batch",
                    "label": "Batches",
                    "sortKey": "batchID"
                },
                {
                    "slot": "type",
                    "label": "Type",
                    "sortKey": "typeID"

                },
                {
                    "slot": "actions",
                    "label": "Actions",
                    "primary" : true,
                    "class":"right aligned collapsing"
                }],
            filters: {
                name:      { filterKey: 'taskName', filterValue: '',
                                filterFunction: (filter, row) => row.toLowerCase().indexOf(filter.toLowerCase()) > -1 },
                start:     { filterKey: 'startDate',        filterValue: '',
                                filterFunction: (filter, row) => new Date(row).getTime() >= new Date(filter).getTime() },
                end:       { filterKey: 'endDate' , filterValue: '',
                                filterFunction: (filter, row) => new Date(row).getTime() <= new Date(filter).getTime() },
                oeMin:     { filterKey: 'originalEstimate', filterValue: '',
                                filterFunction: (filter, row) => parseFloat(row) >= parseFloat(filter) },
                oeMax:     { filterKey: 'originalEstimate', filterValue: '',
                                filterFunction: (filter, row) => parseFloat(row) <= parseFloat(filter) },
                assignee:  { filterKey: 'assignee', filterValue: '',
                                filterFunction: (filter, row) => row.toLowerCase().indexOf(filter.toLowerCase()) > -1 },
                status:    { filterKey: 'status', filterValue: [],
                                filterFunction: (filters, row) => filters.length === 0 || filters.some(filter => row.toLowerCase().indexOf(filter.toLowerCase()) > -1 ) },
                batch:     { filterKey: 'batchID', filterValue: [],
                                filterFunction: (filters, row) => filters.length === 0 || filters.some(filter => parseInt(row) === parseInt(filter)) },
                type:      { filterKey: 'typeID', filterValue: [],
                                filterFunction: (filters, row) => filters.length === 0 || filters.some(filter => parseInt(row) === parseInt(filter)) },
            },
            data: [],
            name: 'tasks',
            configurable : true
        },
        tablePending : {
            cols: [], //will copy table columns
            data: [],
            name: 'pending tasks',
            configurable : true
        },
        tableByBatch : {}
    },
    methods: {
        showGraphModal: function(projectID, task, event){
            $('.graph.modal').modal({ detachable : true, centered: true }).modal('show');
            $.ajax({
                method: "GET",
                url: "/api/tasks/chart?task="+task.taskID,
                success : function(data, textStatus, jqXHR) {

                    let listOfTaskDates = data.listOfTaskDates;
                    let effortSpentDataForChart = data.effortSpentData;

                    //chart config
                    let chart = new Chart($("#lineChart"), {
                        type: 'line',
                        data: {
                            labels: listOfTaskDates,
                            datasets: [{
                                data: effortSpentDataForChart,
                                label: "Effort spent for " + task.taskName,
                                borderColor: "#3e95cd",
                                fill: true,
                                steppedLine: true
                            } ]
                        },
                        options: {
                            title: { display: true, text: 'Task - Effort Spent graph' },
                            scales: {
                                yAxes: [{
                                    ticks: {
                                        min: 0
                                    },
                                    scaleLabel: { display: true, labelString: 'Number of days' }
                                }],
                                xAxes: [{
                                    scaleLabel: { display: true, labelString: 'Dates' }
                                }],
                            }
                        }
                    });

                },
                error: function(jqXHR, textStatus, errorThrown) {
                    console.log(data);
                }
            });
        },
        showCreateTaskModal: function(projectID, task, event){
            event.preventDefault();
            if(task){
                 this.modalTitle = "Edit task";
                // load task data in modal
                 this.newTask.projectID = currentProjectID;
                 this.newTask.taskID = task.taskID;
                 this.newTask.taskName = task.taskName;
                 this.newTask.taskComments = task.taskComments;
                 this.newTask.endDate = task.endDate;
                 this.newTask.startDate = task.startDate;
                 this.newTask.originalEstimate = task.originalEstimate;
                 this.newTask.typeID = task.typeID;
                 this.newTask.status = task.status;
                this.newTask.batchIDs = task.batchIDs;
                this.newTask.batchNames = task.batchNames;
                this.newTask.typeName = task.typeName;
                this.newTask.statusName = task.statusName;
                this.newTask.assignee = task.assignee;
                this.newTask.assigneeID = task.assigneeID;

            }else{
                 this.modalTitle = "Create task";
                 Object.assign(this.newTask , emptyTask);
            }
            let self = this;
            $('.create-task.modal').modal({
                onApprove : function($element) {
                    var validated = $('.create-task .ui.form').form(formValidationRules).form('validate form');
                    var object = {};
                    if(validated) {
                        $('.ui.error.message').hide();
                        $.ajax({
                            method: "POST",
                            url: "/api/tasks",
                            data: JSON.stringify(app.newTask),
                            contentType: "application/json",
                            dataType: "json",
                            success : function(data, textStatus, jqXHR) {
                                window.location.reload();
                                $('.create-task .ui.form').form('reset');
                                $('.create-task.modal').modal('hide');
                            },
                            error: function(jqXHR, textStatus, errorThrown) {
                                $('.ui.error.message').text(jqXHR.responseText);
                                $('.ui.error.message').show();
                            }
                        });
                    }
                    return false;
                },
                detachable : true, centered: true
            }).modal('show');
        },
        approveTask: function(event, task) {
            event.target.classList.toggle('loading');
            $.get("/api/tasks/approve?task="+task.taskID)
                .then(function(data) {
                    task.status = 'IN_PROGRESS';
                    event.target.classList.toggle('loading');
                    app.tablePending.data = app.table.data.filter(r => r.status === 'PENDING');
                });
        },
        deleteTask: function(event, task) {
            this.$refs.confirmModal.confirm("Are you sure you want to delete task "+ task.taskName + "?",
                function() {
                    event.target.classList.toggle('loading');
                    $.get("/api/tasks/delete?task="+task.taskID)
                        .then(function(data) {
                            event.target.classList.toggle('loading');
                            window.location.reload();
                        });
                });

        },
        denyTask: function(event, task) {
            event.target.classList.toggle('loading');
            $.get("/api/tasks/deny?task="+task.taskID)
                .then(function(data){
                    task.status = 'REFUSED';
                    event.target.classList.toggle('loading');
                    app.tablePending.data = app.table.data.filter(r => r.status === 'PENDING');
                });
        },
        toggleFilters : function() {
            $('.filters').toggle();
        }
    },
    created: function () {
        // copying table config to pending task table config
        this.tablePending.cols = this.table.cols;

        let self = this;
        if (currentBatchType !== 'Default') {
            $.ajax({
                type: "GET",
                dataType: "json",
                url: "/api/tasks/batches?project=" + currentProjectID + "&batchType=" + currentBatchType,
                success: function (d) {
                    self.batches = d;
                    d.forEach(function(batch) {
                        self.tableByBatch[batch.batchID] = Object.assign({}, self.table );
                    });
                }
            });
        }

    },
    updated: function () {
        // ! \\ create a infinite loop
       // this.tablePending.data = this.table.data.filter(r => r.status === 'PENDING');
    },
    mounted: function () {
        let self = this;
        $.ajax({
            type: "GET",
            dataType: "json",
            url: "/api/tasks?project=" + currentProjectID,
            success: function (d) {
                self.table.data = d;
                if(currentBatchType !== 'Default') {
                    // Spliting data by batch
                    self.batches.forEach(function(batch) {
                        self.tableByBatch[batch.batchID].data = d.filter(row => { return row.batchIDs.some(b => b === batch.batchID) });
                    });
                }
                self.tablePending.data = d.filter(r => r.status === 'PENDING');
                $('.ui.dimmer').removeClass('active');
            }
        });
    }
});

//Initialization
$(document).ready(function(){
    //init dropdown fields
    $('.ui.multiple.dropdown').dropdown();

    $('.ui.accordion').accordion({exclusive : false});
    //init search fields
    $('.ui.search')
    .search({
        apiSettings: {
            url: '/api/search?q={query}&projectID='+currentProjectID+''
        },
        fields: {
            results : 'items',
            title   : 'screenName',
            description : 'email'
        },
        onSelect: function(result, response) {
            $('.assigned').val(result.screenName);
            $('.taskAssigned').val(result.id);
            app.newTask.assignee = result.screenName;
            app.newTask.assigneeID = result.id;
        },
        minCharacters : 3
    });
});


