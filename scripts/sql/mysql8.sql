
    create table Account (
       id bigint not null,
        accountCreationTime date not null,
        beginWorkDate date not null,
        email varchar(255) not null,
        externalIDs TEXT,
        firstName varchar(255),
        name varchar(255),
        organisation bit,
        remoteSubject varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table AccountHierarchy (
       id bigint not null,
        endDate datetime(6),
        startDate datetime(6) not null,
        child_id bigint,
        parent_id bigint,
        primary key (id)
    ) engine=InnoDB;

    create table Calendar (
       id bigint not null,
        name varchar(50),
        remoteId varchar(100),
        targetType varchar(25),
        primary key (id)
    ) engine=InnoDB;

    create table CostByCategory (
       id bigint not null,
        costPerDay double precision not null,
        costPerHour double precision not null,
        endDate date,
        startDate date not null,
        account_id bigint,
        primary key (id)
    ) engine=InnoDB;

    create table DataTableConfig (
       id bigint not null,
        tableInstanceId varchar(255),
        user_id bigint,
        primary key (id)
    ) engine=InnoDB;

    create table DataTableConfig_columns (
       DataTableConfig_id bigint not null,
        columns varchar(255)
    ) engine=InnoDB;

    create table DefaultTask (
       id bigint not null,
        comments varchar(500),
        endDate date,
        name varchar(100) not null,
        origin varchar(255) not null,
        remoteId varchar(255),
        remotePath varchar(255),
        startDate date,
        primary key (id)
    ) engine=InnoDB;

    create table hibernate_sequence (
       next_val bigint
    ) engine=InnoDB;

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    insert into hibernate_sequence values ( 1 );

    create table Imputation (
       id bigint not null,
        day date,
        value double precision,
        account_id bigint,
        task_id bigint,
        primary key (id)
    ) engine=InnoDB;

    create table Milestone (
       id bigint not null,
        attributes TEXT,
        date date,
        name varchar(50),
        type integer,
        project_id bigint,
        primary key (id)
    ) engine=InnoDB;

    create table Project (
       id bigint not null,
        attributes TEXT,
        comments varchar(500),
        name varchar(50),
        quotation double precision,
        startDate date,
        primary key (id)
    ) engine=InnoDB;

    create table ProjectMembership (
       membershipID bigint not null,
        role varchar(255),
        member_id bigint,
        project_id bigint,
        primary key (membershipID)
    ) engine=InnoDB;

    create table Task (
       id bigint not null,
        comments varchar(500),
        endDate date,
        name varchar(100) not null,
        origin varchar(255) not null,
        remoteId varchar(255),
        remotePath varchar(255),
        startDate date,
        effortLeft double precision not null,
        originalEstimate double precision not null,
        taskStatus integer not null,
        assigned_id bigint,
        milestone_id bigint,
        project_id bigint,
        taskType_id bigint,
        primary key (id)
    ) engine=InnoDB;

    create table TaskRevision (
       id bigint not null,
        effortLeft double precision not null,
        effortSpent double precision not null,
        originalEstimate double precision not null,
        realEffort double precision not null,
        revisionDate datetime(6),
        assigned_id bigint,
        task_id bigint,
        primary key (id)
    ) engine=InnoDB;

    create table TaskType (
       id bigint not null,
        typeName varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table ValidatedTimesheet (
       id bigint not null,
        week integer,
        year integer,
        account_id bigint,
        validatedBy_id bigint,
        primary key (id)
    ) engine=InnoDB;

    alter table Account 
       add constraint UK_l1aov0mnvpvcmg0ctq466ejwm unique (remoteSubject);

    alter table AccountHierarchy 
       add constraint UK75obucy8vq03aqtehoj542edh unique (parent_id, child_id);

    alter table Imputation 
       add constraint UKsc0a68hjsx40d6xt9yep80o7l unique (day, task_id);

    alter table AccountHierarchy 
       add constraint FKpuy6qn63d17dvpcvnfn3786fd 
       foreign key (child_id) 
       references Account (id);

    alter table AccountHierarchy 
       add constraint FKl0m9ft4q7f7poxa8vgc1gxpdp 
       foreign key (parent_id) 
       references Account (id);

    alter table CostByCategory 
       add constraint FKpeelsy07hkv1baei6fv1oo7s2 
       foreign key (account_id) 
       references Account (id);

    alter table DataTableConfig 
       add constraint FKmyycwm902xvsapnqmv1y3r4gj 
       foreign key (user_id) 
       references Account (id);

    alter table DataTableConfig_columns 
       add constraint FK8qwyjho6c0e0ckvebujyixc03 
       foreign key (DataTableConfig_id) 
       references DataTableConfig (id);

    alter table Imputation 
       add constraint FKicayo4omi1a8krucb5t7kipva 
       foreign key (account_id) 
       references Account (id);

    alter table Milestone 
       add constraint FK4y2imlhl4and4511uh6lhnaiy 
       foreign key (project_id) 
       references Project (id);

    alter table ProjectMembership 
       add constraint FK3wl3q3i14wuy156wafo33wlas 
       foreign key (member_id) 
       references Account (id);

    alter table ProjectMembership 
       add constraint FKapg94jqua2lbkjdb0kofxtnln 
       foreign key (project_id) 
       references Project (id);

    alter table Task 
       add constraint FK26uly7piek733vu0rvs6tkusr 
       foreign key (assigned_id) 
       references Account (id);

    alter table Task 
       add constraint FKjl7lj35hlsnb3n8x2kyk9w5lx 
       foreign key (milestone_id) 
       references Milestone (id);

    alter table Task 
       add constraint FKkkcat6aybe3nbvhc54unstxm6 
       foreign key (project_id) 
       references Project (id);

    alter table Task 
       add constraint FKigksw4egslpbdevlab7ucu8lb 
       foreign key (taskType_id) 
       references TaskType (id);

    alter table TaskRevision 
       add constraint FKtiitw2jkye656vww7or0ufx99 
       foreign key (assigned_id) 
       references Account (id);

    alter table TaskRevision 
       add constraint FKpsj9t1js8flo735q3nx3o0c6d 
       foreign key (task_id) 
       references Task (id);

    alter table ValidatedTimesheet 
       add constraint FKfwotsv2gieci2khm1c1aub4uf 
       foreign key (account_id) 
       references Account (id);

    alter table ValidatedTimesheet 
       add constraint FKmw6nt99jgsyfqvnfhpr799tg0 
       foreign key (validatedBy_id) 
       references Account (id);
