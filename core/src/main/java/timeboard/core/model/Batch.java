package timeboard.core.model;

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

import timeboard.core.model.converters.JSONToStringMapConverter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Entity
public class Batch extends OrganizationEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(length = 50, unique = false)
    private String name;

    @Temporal(TemporalType.DATE)
    private Date date;

    @Column(length = 500)
    private BatchType type;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JSONToStringMapConverter.class)
    @Lob
    private Map<String, String> attributes;

    @ManyToOne(targetEntity = Project.class, fetch = FetchType.EAGER)
    private Project project;

    @ManyToMany(
            targetEntity = Task.class,
            mappedBy = "batches",
            fetch = FetchType.LAZY)
    private Set<Task> tasks;


    public Batch(long id,
                 String name,
                 Date date,
                 BatchType type,
                 Map<String, String> attributes, Project project, Set<Task> tasks) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.type = type;
        this.attributes = attributes;
        this.project = project;
        this.tasks = tasks;
    }

    public Batch() {
        this.tasks = new HashSet<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        if(this.getType() != BatchType.GROUP) {
            this.date = date;
        }
    }

    public BatchType getType() {
        return type;
    }

    public void setType(BatchType type) {
        this.type = type;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public Set<Task> getTasks() {
        return tasks;
    }

    public void setTasks(Set<Task> tasks) {
        this.tasks = tasks;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }


}
