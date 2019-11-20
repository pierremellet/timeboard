package timeboard.core.api;

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

import net.fortuna.ical4j.data.ParserException;
import timeboard.core.api.exceptions.BusinessException;
import timeboard.core.model.*;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

public interface CalendarService {

    boolean importCalendarAsTasksFromICS(User actor, String name, String ICS, Project project, boolean deleteOrphan) throws BusinessException, ParseException, IOException;

    boolean importCalendarAsImputationsFromICS(User actor,String ICS, AbstractTask task, List<User> userList, double value) throws BusinessException, ParseException, IOException ;

    Calendar createOrUpdateCalendar(String name, String remoteId);

    List<Calendar> listCalendars();

    List<DefaultTask> findExistingEvents(String remotePath, String remoteId);

    Map<String, List<Task>> findAllEventAsTask(Calendar calendar, Project project);

    void deleteCalendarById(User actor, Long calendarID) throws BusinessException;



}