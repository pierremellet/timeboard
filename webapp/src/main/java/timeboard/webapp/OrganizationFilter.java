package timeboard.webapp;

/*-
 * #%L
 * webapp
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import timeboard.core.api.ThreadLocalStorage;
import timeboard.core.ui.UserInfo;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Order(1)
public class OrganizationFilter implements Filter {

    @Autowired
    private UserInfo userInfo;

    private static final List<String> whitelist = new ArrayList<>();
    static{
        whitelist.add(OrganizationSelectController.URI);
        whitelist.add(OnboardingController.URI);
        whitelist.add(".*(.)(js|css|jpg|png|ttf|woff|woff2)");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if(isWhiteListed((HttpServletRequest)servletRequest)){
            filterChain.doFilter(servletRequest, servletResponse);
        }else {
            Optional<Cookie> orgCookie = this.extractOrgCookie((HttpServletRequest) servletRequest);
            if(orgCookie.isPresent()){
                ThreadLocalStorage.setCurrentOrganizationID(Long.parseLong(orgCookie.get().getValue()));
            }else{
                ((HttpServletResponse)servletResponse).sendRedirect(OrganizationSelectController.URI);
            }

            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    private boolean isWhiteListed(HttpServletRequest servletRequest) {

        final Long nbRulesMatched = whitelist.stream().filter(s -> servletRequest.getRequestURI().matches(s)).collect(Collectors.counting());

        return nbRulesMatched != null && nbRulesMatched > 0;
    }

    private Optional<Cookie> extractOrgCookie(HttpServletRequest servletRequest) {
        return Arrays.asList(servletRequest.getCookies())
                .stream()
                .filter(cookie -> cookie.getName().equals(OrganizationSelectController.COOKIE_NAME))
                .findFirst();
    }
}