package timeboard.core.internal;

/*-
 * #%L
 * core
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

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import timeboard.core.api.CalendarService;
import timeboard.core.api.ProjectService;
import timeboard.core.api.exceptions.BusinessException;
import timeboard.core.internal.rules.Rule;
import timeboard.core.internal.rules.RuleSet;
import timeboard.core.model.*;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.*;


@org.springframework.stereotype.Component
public class CalendarServiceImpl implements CalendarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarServiceImpl.class);
    private static final String CALENDAR_ORIGIN_KEY = "calendar";
    @Autowired
    private EntityManager em;
    @Autowired
    private ProjectService projectService;

    @Override
    public boolean importCalendarAsImputationsFromIcs(
            Account actor, String url, AbstractTask task, List<Account> accountList,
            double value) throws BusinessException, ParseException, IOException {

        /* -- Events -- */
        final Set<Imputation> existingEventList = task.getImputations();
        final Map<String, List<Event>> newEvents = this.importICS(url); // imported events

        final List<Imputation> imputationsToUpdate = new ArrayList<>();

        for (final List<Event> newEventList : newEvents.values()) {
            for (final Event event : newEventList) {
                if (existingEventList == null || existingEventList.isEmpty()) { // no existing events for this id
                    imputationsToUpdate.addAll(this.eventToImputation(event, task, accountList, value)); // so create it
                } else { //  one or many events exist for this id
                    final Imputation timeboardImputation = this.getImputationByStartDate(existingEventList, event.getStartDate());
                    if (timeboardImputation != null) { // event and task match (id & date), so update it
                        // convert event to imputation
                        imputationsToUpdate.addAll(this.eventToImputation(event, task, accountList, value));
                        existingEventList.remove(timeboardImputation); // remove  to retrieve orphan at the end
                    } else { // no matching imputation found, so create it
                        imputationsToUpdate.addAll(this.eventToImputation(event, task, accountList, value));
                    }
                }
            }
        }
        this.projectService.updateTaskImputations(actor, imputationsToUpdate);

        return true;
    }

    @Override
    public boolean importCalendarAsTasksFromIcs(
            Account actor,
            String name,
            String url,
            Project project,
            boolean deleteOrphan) throws ParseException, IOException {

        /* -- Calendar -- */
        final timeboard.core.model.Calendar timeboardCalendar = this.createOrUpdateCalendar(name, url);

        /* -- Events -- */
        final Map<String, List<Task>> existingEvents = this.findAllEventAsTask(timeboardCalendar, project);
        final Map<String, List<Event>> newEvents = this.importICS(url); // imported events

        final List<Task> tasksToCreate = new ArrayList<>();
        final List<Task> tasksToUpdate = new ArrayList<>();
        final List<Task> tasksToDelete = new ArrayList<>();

        for (List<Event> newEventList : newEvents.values()) {
            List<Task> existingEventList = existingEvents.get(newEventList.get(0).getRemoteId());
            for (Event event : newEventList) {
                if (existingEventList == null || existingEventList.isEmpty()) { // no existing events for this id
                    tasksToCreate.add((Task) this.eventToTask(event, new Task(), project)); // so create it
                } else { //  one or many events exist for this id
                    Task timeboardEvent = this.getTaskByStartDate(existingEventList, event.getStartDate());
                    if (timeboardEvent != null) { // event and task match (id & date), so update it
                        tasksToUpdate.add((Task) this.eventToTask(event, timeboardEvent, project));// convert event to task
                        existingEventList.remove(timeboardEvent); // remove  to retrieve orphan at the end
                    } else { // no matching task found, so create it
                        tasksToCreate.add((Task) this.eventToTask(event, new Task(), project));
                    }
                }
            }
        }

        if (deleteOrphan) {
            for (List<Task> remainingEventList : existingEvents.values()) {
                tasksToDelete.addAll(remainingEventList);
            }
            this.projectService.deleteTasks(actor, tasksToDelete);
        }
        this.projectService.createTasks(actor, tasksToCreate);
        this.projectService.updateTasks(actor, tasksToUpdate);

        return true;
    }

    /**
     * Import ICS as Event.
     *
     * @param url ics file url
     * @return map key = remoteId, value = Event
     * @throws ParseException when ICS parser failed
     * @throws IOException    when fil is not found
     */
    private Map<String, List<Event>> importICS(final String url) throws IOException, ParseException {

        final Map<String, List<Event>> events = new HashMap<>();

        final net.fortuna.ical4j.model.Calendar parsedCalendar;
        final CalendarBuilder builder = new CalendarBuilder();
        final FileInputStream fin = new FileInputStream(url);
        try {
            parsedCalendar = builder.build(fin);
        } catch (ParserException e) {
            throw new ParseException(e.getMessage(), e.getLineNo());
        }

        for (Object o : parsedCalendar.getComponents(Component.VEVENT)) {

            VEvent parsedEvent = (VEvent) o;
            Event event = new Event();

            this.icsToEvent(parsedEvent, event);
            event.setRemotePath(url);
            event.setRemoteId(parsedEvent.getUid().getValue());

            Property propertyRRule = parsedEvent.getProperty(Property.RRULE);
            if (propertyRRule != null) {
                this.createRecurringEvents(event, (RRule) propertyRRule, events);
            } else {
                List<Event> eventList = events.computeIfAbsent(event.getRemoteId(), k -> new ArrayList<>());
                eventList.add(event);
            }
        }

        LOGGER.info("Import successful ");

        return events;
    }

    private void icsToEvent(VEvent icsEvent, Event timeboardEvent) throws ParseException {

        Uid uid = icsEvent.getUid();

        if (uid != null) {
            timeboardEvent.setRemoteId(uid.getValue());
        }

        Summary summary = icsEvent.getSummary();
        if (summary != null) {
            timeboardEvent.setName(summary.getValue());
        } else {
            timeboardEvent.setName("");
        }

        Description description = icsEvent.getDescription();
        if (description != null) {
            timeboardEvent.setComments(description.getValue());
        } else {
            timeboardEvent.setComments("");
        }

        timeboardEvent.setStartDate(this.getJavaDateFromProperty(icsEvent.getStartDate()));
        timeboardEvent.setEndDate(this.getJavaDateFromProperty(icsEvent.getEndDate()));
        if (timeboardEvent.getEndDate() == null) {
            timeboardEvent.setEndDate(timeboardEvent.getStartDate()); //if no end date then set it to start date
        }

        timeboardEvent.setOrigin(CALENDAR_ORIGIN_KEY);

    }

    private AbstractTask eventToTask(Event event, Task task, Project project) {

        this.eventToTask(event, task);
        task.setProject((project));
        return task;

    }

    private void eventToTask(Event event, AbstractTask task) {

        task.setRemoteId(event.getRemoteId());
        task.setRemotePath(event.getRemotePath());

        task.setName(event.getName());
        task.setComments(event.getComments());

        task.setStartDate(event.getStartDate());
        task.setEndDate(event.getEndDate());

        task.setOrigin(event.getOrigin());

    }

    private List<Imputation> eventToImputation(Event event, AbstractTask task, List<Account> accountList, double value) {
        List<Imputation> result = new ArrayList<>();
        for (Account account : accountList) {
            Imputation imputation = new Imputation();
            imputation.setAccount(account);
            imputation.setTask(task);
            imputation.setDay(event.getStartDate());
            imputation.setValue(value);
            result.add(imputation);
        }
        return result;

    }

    private void createRecurringEvents(Event originalEvent, RRule rule, Map<String, List<Event>> events) {
        // Today
        Calendar startDate = Calendar.getInstance();
        startDate.set(Calendar.HOUR_OF_DAY, 9);

        // Today + 1 year
        java.util.Calendar endDate = Calendar.getInstance();
        endDate.set(Calendar.HOUR_OF_DAY, 9);
        endDate.roll(Calendar.YEAR, 1);

        // Create recurring tasks from recurring rules
        Recur recur = rule.getRecur();
        DateList dates = recur.getDates(
                new net.fortuna.ical4j.model.Date(startDate.getTime()),
                new net.fortuna.ical4j.model.Date(endDate.getTime()),
                Value.DATE);

        for (Date icsDate : (Date[]) dates.toArray(new Date[0])) {
            //TODO start and end date are set equals  is this functionally correct ?
            Event event = this.cloneWithDate(originalEvent, icsDate, icsDate);
            List<Event> eventList = events.computeIfAbsent(event.getRemoteId(), k -> new ArrayList<>());
            eventList.add(event);
        }
    }

    private Task getTaskByStartDate(Collection<Task> tasks, java.util.Date date) {
        Task result = null;
        for (Task t : tasks) {
            if (t.getStartDate().equals(date)) {
                result = t;
            }
        }
        return result;
    }

    private Imputation getImputationByStartDate(Collection<Imputation> imputations, java.util.Date date) {
        Imputation result = null;
        for (Imputation i : imputations) {
            if (i.getDay().equals(date)) {
                result = i;
            }
        }
        return result;
    }

    public timeboard.core.model.Calendar createOrUpdateCalendar(String name, String remoteId) {

        timeboard.core.model.Calendar calendar = null;

        try {
            TypedQuery<timeboard.core.model.Calendar> q = em.createQuery(
                    "select c from Calendar c where c.remoteId = :remoteId",
                    timeboard.core.model.Calendar.class);

            q.setParameter("remoteId", remoteId);
            calendar = q.getSingleResult();

        } catch (Exception e) {
            // calendar not already exist
        }
        if (calendar == null) { // create
            timeboard.core.model.Calendar newCalendar = new timeboard.core.model.Calendar();
            newCalendar.setRemoteId(remoteId);
            newCalendar.setName(name);
            em.persist(newCalendar);
            return newCalendar;
        } else { // update
            final timeboard.core.model.Calendar toUpdateCalendar = calendar;
            toUpdateCalendar.setName(name);
            toUpdateCalendar.setRemoteId(remoteId);
            em.merge(toUpdateCalendar);
            return toUpdateCalendar;
        }


    }

    @Override
    public List<timeboard.core.model.Calendar> listCalendars() {
        TypedQuery<timeboard.core.model.Calendar> q =
                em.createQuery("select c from Calendar c", timeboard.core.model.Calendar.class);

        return q.getResultList();
    }

    @Override
    public List<DefaultTask> findExistingEvents(String remotePath, String remoteId) {
        TypedQuery<DefaultTask> q = em.createQuery("select d from DefaultTask d " +
                "where d.remotePath = :remotePath and d.remoteId = :remoteId", DefaultTask.class);

        q.setParameter("remotePath", remotePath);
        q.setParameter("remoteId", remoteId);
        q.setParameter("origin", CALENDAR_ORIGIN_KEY);
        return q.getResultList();
    }

    @Override
    public Map<String, List<Task>> findAllEventAsTask(timeboard.core.model.Calendar calendar, Project project) {
        Map<String, List<Task>> idToEventList = new HashMap<>();
        try {
            TypedQuery<Task> q = em.createQuery("select t from Task t " +
                    "where t.remotePath = :remotePath and t.origin = :origin and t.project = :project", Task.class);

            q.setParameter("remotePath", calendar.getRemoteId());
            q.setParameter("origin", CALENDAR_ORIGIN_KEY);
            q.setParameter("project", project);
            List<Task> eventList = q.getResultList();

            for (Task event : eventList) {
                List<Task> currentList = idToEventList.get(event.getRemoteId());
                if (currentList == null) {
                    currentList = idToEventList.put(event.getRemoteId(), new ArrayList<>());
                }
                assert currentList != null;
                currentList.add(event);
            }
        } catch (Exception e) {
            // handle JPA exception, nothing more to do
        }
        return idToEventList;
    }

    @Override
    public void deleteCalendarById(Account actor, Long calendarID) throws BusinessException {
        RuleSet<timeboard.core.model.Calendar> ruleSet = new RuleSet<>();

        timeboard.core.model.Calendar calendar = em.find(timeboard.core.model.Calendar.class, calendarID);

        Set<Rule> wrongRules = ruleSet.evaluate(actor, calendar);
        if (!wrongRules.isEmpty()) {
            throw new BusinessException(wrongRules);
        }

        TypedQuery<DefaultTask> query = em.createQuery("select e from DefaultTask where e.remotePath = :remotePath", DefaultTask.class);
        query.setParameter("remotePath", calendar.getRemoteId());
        try {
            List<DefaultTask> eventList = query.getResultList();
            for (DefaultTask event : eventList) {
                for (Imputation i : event.getImputations()) {
                    em.remove(i); //remove all imputation for this event
                }
                em.remove(event);
            }

        } catch (Exception e) {
            // no event to delete
        }

        em.remove(calendar);
        em.flush();

        LOGGER.info("Calendar " + calendarID + " deleted by " + actor.getName());

    }

    private Event cloneWithDate(Event source, Date startDate, Date endDate) {

        Event clone = (Event) source.clone();

        clone.setStartDate(startDate);
        clone.setEndDate(endDate);

        return clone;
    }

    private java.util.Date getJavaDateFromProperty(Property p) throws ParseException {
        java.util.Date date = null;
        if (p != null) {
            SimpleDateFormat sdf;
            if (p.getValue().length() == 16) {
                sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
            } else {
                sdf = new SimpleDateFormat("yyyyMMdd");
            }
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
            date = sdf.parse(p.getValue());
        }

        return date;
    }

}
