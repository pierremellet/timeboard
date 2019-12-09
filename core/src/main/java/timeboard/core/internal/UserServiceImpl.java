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

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import timeboard.core.api.UserService;
import timeboard.core.api.exceptions.BusinessException;
import timeboard.core.model.Account;
import timeboard.core.model.Project;

import javax.persistence.*;
import javax.transaction.Transactional;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;



@Component
@Transactional
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private EntityManager em;

    @Override
    public List<Account> createUsers(List<Account> accounts) {
             accounts.forEach(user -> {
                em.persist(user);
                 LOGGER.info("User " + user.getFirstName() + " " + user.getName() + " created");
            });
            return accounts;
     }

    @Override
    @Transactional
    public Account createUser(final Account account) throws BusinessException {
        this.em.persist(account);
        LOGGER.info("User " + account.getFirstName() + " " + account.getName() + " created");
        this.em.flush();
        return account;
     }


    @Override
    public Account updateUser(Account account) {
             Account u = this.findUserByID(account.getId());
            if (u != null) {
                u.setFirstName(account.getFirstName());
                u.setName(account.getName());
                u.setEmail(account.getEmail());
                u.setExternalIDs(account.getExternalIDs());
            }
            em.flush();
            LOGGER.info("User " + account.getEmail() + " updated.");
            return account;

    }


    @Override
    public List<Account> searchUserByEmail(final String prefix) {
             TypedQuery<Account> q = em
                    .createQuery(
                            "select u from User u "
                                    + "where u.email LIKE CONCAT('%',:prefix,'%')",
                            Account.class);
            q.setParameter("prefix", prefix);
            return q.getResultList();
     }

    @Override
    public List<Account> searchUserByEmail(final String prefix, final Long projectId) {
             Project project = em.find(Project.class, projectId);
            List<Account> matchedAccount = project.getMembers().stream()
                    .filter(projectMembership -> projectMembership
                            .getMember()
                            .getEmail().startsWith(prefix))
                    .map(projectMembership -> projectMembership.getMember())
                    .collect(Collectors.toList());
            return matchedAccount;
     }


    @Override
    public Account findUserByID(final Long userID) {
        if (userID == null) {
            return null;
        }
        return em.find(Account.class, userID);
    }

    @Override
    public Account findUserByEmail(String email) {
        if (email == null) {
            return null;
        }
             TypedQuery<Account> q = em.createQuery("from User u where u.email=:email", Account.class);
            q.setParameter("email", email);
            Account account;
            try {
                account = q.getSingleResult();
            } catch (NoResultException e) {
                account = null;
            }
            return account;
     }

    @Override
    public Account findUserBySubject(String remoteSubject) {
        Account u;
        try {
                 Query q = em
                        .createQuery("select u from User u where u.remoteSubject = :sub", Account.class);
                q.setParameter("sub", remoteSubject);
                return (Account) q.getSingleResult();
         } catch (NoResultException | NonUniqueResultException e) {
            u = null;
        }
        return u;
    }


    @Override
    public Account findUserByExternalID(String origin, String userExternalID) {
        Account u;
        try {
                 Query q = em // MYSQL native for JSON queries
                        .createNativeQuery("select * from User "
                                + "where JSON_EXTRACT(externalIDs, '$." + origin + "')"
                                + " = ?", Account.class);
                q.setParameter(1, userExternalID);
                return (Account) q.getSingleResult();
         } catch (javax.persistence.NoResultException e) {
            u = null;
        }
        return u;
    }

    @Override
    @Transactional
    public Account userProvisionning(String sub, String email) throws BusinessException {

        Account account = this.findUserBySubject(sub);
        if (account == null) {
            //Create user
            account = new Account(null, null, email, new Date(), new Date());
            account.setRemoteSubject(sub);
            account = this.createUser(account);
        }
        return account;
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    private boolean checkPassword(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }
}
