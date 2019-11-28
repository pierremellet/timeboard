package timeboard.core.observers.emails;

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

import io.reactivex.schedulers.Schedulers;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import timeboard.core.api.EmailService;
import timeboard.core.api.TimeboardSubjects;
import timeboard.core.internal.TemplateGenerator;
import timeboard.core.internal.events.TaskEvent;
import timeboard.core.internal.events.TimeboardEvent;
import timeboard.core.internal.events.TimeboardEventType;
import timeboard.core.internal.events.TimesheetEvent;
import timeboard.core.model.EmailSummaryModel;
import timeboard.core.model.Task;
import timeboard.core.model.User;
import timeboard.core.model.ValidatedTimesheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component(
        service = SendSummaryEmail.class,
        immediate = true
)
public class SendSummaryEmail {

    @Reference
    private EmailService emailService;

    private TemplateGenerator templateGenerator = new TemplateGenerator();

    @Activate
    public void activate(){
        TimeboardSubjects.TIMEBOARD_EVENTS // Listen for all timeboard app events
                .observeOn(Schedulers.from(Executors.newFixedThreadPool(10))) // Observe on 10 threads
                .buffer(5, TimeUnit.MINUTES) // Aggregate mails every 5 minutes TODO add configuration
                .map(this::notificationEventToUserEvent) // Rebalance events by user to notify/inform
                .flatMapIterable(l -> l) // transform user list to single events
                .subscribe(struc ->this.emailService.sendMessage(generateMailFromEventList(struc))); //create and send individual summary
    }

    /**
     * Transform user with his notifications to email structure
     * Work for task create/delete events and timesheet validation events
     * @param userNotificationStructure structure user 1 -- * events to notify/inform
     * @return email ready structure
     */
    private EmailStructure generateMailFromEventList(UserNotificationStructure userNotificationStructure) {

        Map<String, Object> data  = new HashMap<>();

        List<ValidatedTimesheet> validatedTimesheets = new ArrayList<>();
        Map<Long, EmailSummaryModel> projects = new HashMap<>();

        String subject = "[Timeboard] Daily summary";

        for(TimeboardEvent event : userNotificationStructure.getNotificationEventList()){
            if(event instanceof TaskEvent){
                Task t = ((TaskEvent) event).getTask();
                if(((TaskEvent) event).getEventType() == TimeboardEventType.CREATE) projects.computeIfAbsent(t.getProject().getId(), e -> new EmailSummaryModel(t.getProject())).addCreatedTask((TaskEvent) event);
                if(((TaskEvent) event).getEventType() == TimeboardEventType.DELETE) projects.computeIfAbsent(t.getProject().getId(), e -> new EmailSummaryModel(t.getProject())).addDeletedTask((TaskEvent) event);
            } else if(event instanceof TimesheetEvent){
                validatedTimesheets.add(((TimesheetEvent) event).getTimesheet());
            }
        }
        data.put("projects", projects.values());
        data.put("validatedTimesheets", validatedTimesheets);

        String message = templateGenerator.getTemplateString("core-ui:mail/summary.html", data);
                ArrayList<String> list = new ArrayList<>();
                list.add(userNotificationStructure.getTargetUser().getEmail());
        return new EmailStructure(list, null, subject, message);
    }


    /**
     * Rebalance events by user to notify/inform
     * @param events list of events
     * @return userNotificationStructure structure user 1 -- * events to notify/inform
     */
    private List<UserNotificationStructure> notificationEventToUserEvent(List<TimeboardEvent> events) {
        HashMap<Long, UserNotificationStructure> dataList = new HashMap<>();

        for(TimeboardEvent event : events){
            for(User user : event.getUsersToNotify()){
                dataList.computeIfAbsent(user.getId(), t -> new UserNotificationStructure(user)).notify(event);
            }
            for(User user : event.getUsersToInform()){
                dataList.computeIfAbsent(user.getId(), t -> new UserNotificationStructure(user)).inform(event);
            }
        }
        return new ArrayList<>(dataList.values());
    }





}