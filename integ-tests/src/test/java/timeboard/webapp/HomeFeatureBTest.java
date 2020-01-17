package timeboard.webapp;

/*-
 * #%L
 * integ-tests
 * %%
 * Copyright (C) 2019 - 2020 Timeboard
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

import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import timeboard.core.api.ThreadLocalStorage;
import timeboard.core.api.exceptions.BusinessException;
import timeboard.core.model.Account;
import timeboard.core.model.MembershipRole;
import timeboard.core.model.Organization;
import timeboard.core.model.OrganizationMembership;
import timeboard.core.security.TimeboardAuthentication;
import timeboard.home.HomeController;

import java.util.Collections;
import java.util.UUID;

public class HomeFeatureBTest extends TimeboardTest {

    @Autowired
    protected HomeController homeController;

    private Account account;
    private Organization organisationA;
    private Organization organisationB;
    private TimeboardAuthentication auth;
    private Model model;




    @Given("^user with an existing account and (\\d+) project in (\\d+) org \\(A\\) and (\\d+) in an other org \\(B\\)$")
    public void user_with_an_existing_account_and_project_in_org_A_and_in_an_other_org_B(int arg1, int arg2, int arg3) throws Throwable {
        this.model = new ConcurrentModel();

        this.organisationA = this.organizationService.createOrganization("Integration A", Collections.emptyMap());
        this.organisationB = this.organizationService.createOrganization("Integration B", Collections.emptyMap());

        this.account = this.userService.userProvisionning(UUID.randomUUID().toString(), "test2");

        this.auth = new TimeboardAuthentication(this.account);

        this.account.getOrganizations().add(new OrganizationMembership(this.organisationA, this.account, MembershipRole.OWNER));
        this.account.getOrganizations().add(new OrganizationMembership(this.organisationB, this.account, MembershipRole.OWNER));


        this.auth.setCurrentOrganization(this.organisationA.getId());
        SecurityContextHolder.getContext().setAuthentication(this.auth);
        ThreadLocalStorage.setCurrentOrganizationID(this.organisationA.getId());
        this.projectService.createProject(this.account, "TestProjectOrgA");

        this.auth.setCurrentOrganization(this.organisationB.getId());
        ThreadLocalStorage.setCurrentOrganizationID(this.organisationB.getId());
        SecurityContextHolder.getContext().setAuthentication(this.auth);
        this.projectService.createProject(this.account, "TestProjectOrgB");


        this.auth.setCurrentOrganization(this.organisationA.getId());
        ThreadLocalStorage.setCurrentOrganizationID(this.organisationA.getId());
        SecurityContextHolder.getContext().setAuthentication(this.auth);

    }

    @When("^the user calls /home on org A$")
    public void the_user_calls_home_on_org_A() throws Throwable {
        this.homeController.handleGet(this.auth, this.model);
    }

    @Then("^the user receives (\\d+) project from org A$")
    public void the_user_receives_project_form_org_A(int arg1) throws Throwable {
        Assert.assertEquals(this.model.asMap().get(HomeController.NB_PROJECTS), arg1);
    }


}