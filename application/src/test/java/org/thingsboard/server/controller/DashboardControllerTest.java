/**
 * Copyright © 2016-2017 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.controller;

import static org.hamcrest.Matchers.containsString;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.model.ModelConstants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.type.TypeReference;

public class DashboardControllerTest extends AbstractControllerTest {
    
    private IdComparator<Dashboard> idComparator = new IdComparator<>();
    
    private Tenant savedTenant;
    private User tenantAdmin;
    
    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
        
        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        Assert.assertNotNull(savedTenant);
        
        tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(savedTenant.getId());
        tenantAdmin.setEmail("tenant2@thingsboard.org");
        tenantAdmin.setFirstName("Joe");
        tenantAdmin.setLastName("Downs");
        
        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");
    }
    
    @After
    public void afterTest() throws Exception {
        loginSysAdmin();
        
        doDelete("/api/tenant/"+savedTenant.getId().getId().toString())
        .andExpect(status().isOk());
    }
    
    @Test
    public void testSaveDashboard() throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("My dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
        
        Assert.assertNotNull(savedDashboard);
        Assert.assertNotNull(savedDashboard.getId());
        Assert.assertTrue(savedDashboard.getCreatedTime() > 0);
        Assert.assertEquals(savedTenant.getId(), savedDashboard.getTenantId());
        Assert.assertNotNull(savedDashboard.getCustomerId());
        Assert.assertEquals(NULL_UUID, savedDashboard.getCustomerId().getId());
        Assert.assertEquals(dashboard.getTitle(), savedDashboard.getTitle());
        
        savedDashboard.setTitle("My new dashboard");
        doPost("/api/dashboard", savedDashboard, Dashboard.class);
        
        Dashboard foundDashboard = doGet("/api/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        Assert.assertEquals(foundDashboard.getTitle(), savedDashboard.getTitle());
    }
    
    @Test
    public void testFindDashboardById() throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("My dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
        Dashboard foundDashboard = doGet("/api/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        Assert.assertNotNull(foundDashboard);
        Assert.assertEquals(savedDashboard, foundDashboard);
    }
    
    @Test
    public void testDeleteDashboard() throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("My dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
        
        doDelete("/api/dashboard/"+savedDashboard.getId().getId().toString())
        .andExpect(status().isOk());

        doGet("/api/dashboard/"+savedDashboard.getId().getId().toString())
        .andExpect(status().isNotFound());
    }
    
    @Test
    public void testSaveDashboardWithEmptyTitle() throws Exception {
        Dashboard dashboard = new Dashboard();
        doPost("/api/dashboard", dashboard)
        .andExpect(status().isBadRequest())
        .andExpect(statusReason(containsString("Dashboard title should be specified")));
    }
    
    @Test
    public void testAssignUnassignDashboardToCustomer() throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("My dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
        
        Customer customer = new Customer();
        customer.setTitle("My customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);
        
        Dashboard assignedDashboard = doPost("/api/customer/" + savedCustomer.getId().getId().toString() 
                + "/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        Assert.assertEquals(savedCustomer.getId(), assignedDashboard.getCustomerId());
        
        Dashboard foundDashboard = doGet("/api/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        Assert.assertEquals(savedCustomer.getId(), foundDashboard.getCustomerId());

        Dashboard unassignedDashboard = 
                doDelete("/api/customer/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        Assert.assertEquals(ModelConstants.NULL_UUID, unassignedDashboard.getCustomerId().getId());
        
        foundDashboard = doGet("/api/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        Assert.assertEquals(ModelConstants.NULL_UUID, foundDashboard.getCustomerId().getId());
    }
    
    @Test
    public void testAssignDashboardToNonExistentCustomer() throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("My dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
        
        doPost("/api/customer/" + UUIDs.timeBased().toString() 
                + "/dashboard/" + savedDashboard.getId().getId().toString())
        .andExpect(status().isNotFound());
    }
    
    @Test
    public void testAssignDashboardToCustomerFromDifferentTenant() throws Exception {
        loginSysAdmin();
        
        Tenant tenant2 = new Tenant();
        tenant2.setTitle("Different tenant");
        Tenant savedTenant2 = doPost("/api/tenant", tenant2, Tenant.class);
        Assert.assertNotNull(savedTenant2);

        User tenantAdmin2 = new User();
        tenantAdmin2.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin2.setTenantId(savedTenant2.getId());
        tenantAdmin2.setEmail("tenant3@thingsboard.org");
        tenantAdmin2.setFirstName("Joe");
        tenantAdmin2.setLastName("Downs");
        
        tenantAdmin2 = createUserAndLogin(tenantAdmin2, "testPassword1");
        
        Customer customer = new Customer();
        customer.setTitle("Different customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        login(tenantAdmin.getEmail(), "testPassword1");
        
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("My dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
        
        doPost("/api/customer/" + savedCustomer.getId().getId().toString()
                + "/dashboard/" + savedDashboard.getId().getId().toString())
        .andExpect(status().isForbidden());
        
        loginSysAdmin();
        
        doDelete("/api/tenant/"+savedTenant2.getId().getId().toString())
        .andExpect(status().isOk());
    }

    @Test
    public void testFindTenantDashboards() throws Exception {
        List<Dashboard> dashboards = new ArrayList<>();
        for (int i=0;i<173;i++) {
            Dashboard dashboard = new Dashboard();
            dashboard.setTitle("Dashboard"+i);
            dashboards.add(doPost("/api/dashboard", dashboard, Dashboard.class));
        }
        List<Dashboard> loadedDashboards = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(24);
        TextPageData<Dashboard> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/dashboards?", 
                    new TypeReference<TextPageData<Dashboard>>(){}, pageLink);
            loadedDashboards.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());
        
        Collections.sort(dashboards, idComparator);
        Collections.sort(loadedDashboards, idComparator);
        
        Assert.assertEquals(dashboards, loadedDashboards);
    }
    
    @Test
    public void testFindTenantDashboardsByTitle() throws Exception {
        String title1 = "Dashboard title 1";
        List<Dashboard> dashboardsTitle1 = new ArrayList<>();
        for (int i=0;i<134;i++) {
            Dashboard dashboard = new Dashboard();
            String suffix = RandomStringUtils.randomAlphanumeric((int)(Math.random()*15));
            String title = title1+suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            dashboard.setTitle(title);
            dashboardsTitle1.add(doPost("/api/dashboard", dashboard, Dashboard.class));
        }
        String title2 = "Dashboard title 2";
        List<Dashboard> dashboardsTitle2 = new ArrayList<>();
        for (int i=0;i<112;i++) {
            Dashboard dashboard = new Dashboard();
            String suffix = RandomStringUtils.randomAlphanumeric((int)(Math.random()*15));
            String title = title2+suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            dashboard.setTitle(title);
            dashboardsTitle2.add(doPost("/api/dashboard", dashboard, Dashboard.class));
        }
        
        List<Dashboard> loadedDashboardsTitle1 = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(15, title1);
        TextPageData<Dashboard> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/dashboards?", 
                    new TypeReference<TextPageData<Dashboard>>(){}, pageLink);
            loadedDashboardsTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());
        
        Collections.sort(dashboardsTitle1, idComparator);
        Collections.sort(loadedDashboardsTitle1, idComparator);
        
        Assert.assertEquals(dashboardsTitle1, loadedDashboardsTitle1);
        
        List<Dashboard> loadedDashboardsTitle2 = new ArrayList<>();
        pageLink = new TextPageLink(4, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/dashboards?", 
                    new TypeReference<TextPageData<Dashboard>>(){}, pageLink);
            loadedDashboardsTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(dashboardsTitle2, idComparator);
        Collections.sort(loadedDashboardsTitle2, idComparator);
        
        Assert.assertEquals(dashboardsTitle2, loadedDashboardsTitle2);
        
        for (Dashboard dashboard : loadedDashboardsTitle1) {
            doDelete("/api/dashboard/"+dashboard.getId().getId().toString())
            .andExpect(status().isOk());
        }
        
        pageLink = new TextPageLink(4, title1);
        pageData = doGetTypedWithPageLink("/api/tenant/dashboards?", 
                new TypeReference<TextPageData<Dashboard>>(){}, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
        
        for (Dashboard dashboard : loadedDashboardsTitle2) {
            doDelete("/api/dashboard/"+dashboard.getId().getId().toString())
            .andExpect(status().isOk());
        }
        
        pageLink = new TextPageLink(4, title2);
        pageData = doGetTypedWithPageLink("/api/tenant/dashboards?", 
                new TypeReference<TextPageData<Dashboard>>(){}, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }
    
    @Test
    public void testFindCustomerDashboards() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("Test customer");
        customer = doPost("/api/customer", customer, Customer.class);
        CustomerId customerId = customer.getId();
        
        List<Dashboard> dashboards = new ArrayList<>();
        for (int i=0;i<173;i++) {
            Dashboard dashboard = new Dashboard();
            dashboard.setTitle("Dashboard"+i);
            dashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
            dashboards.add(doPost("/api/customer/" + customerId.getId().toString() 
                            + "/dashboard/" + dashboard.getId().getId().toString(), Dashboard.class));
        }
        
        List<Dashboard> loadedDashboards = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(21);
        TextPageData<Dashboard> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/dashboards?", 
                    new TypeReference<TextPageData<Dashboard>>(){}, pageLink);
            loadedDashboards.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());
        
        Collections.sort(dashboards, idComparator);
        Collections.sort(loadedDashboards, idComparator);
        
        Assert.assertEquals(dashboards, loadedDashboards);
    }
    
    @Test
    public void testFindCustomerDashboardsByTitle() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("Test customer");
        customer = doPost("/api/customer", customer, Customer.class);
        CustomerId customerId = customer.getId();

        String title1 = "Dashboard title 1";
        List<Dashboard> dashboardsTitle1 = new ArrayList<>();
        for (int i=0;i<125;i++) {
            Dashboard dashboard = new Dashboard();
            String suffix = RandomStringUtils.randomAlphanumeric((int)(Math.random()*15));
            String title = title1+suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            dashboard.setTitle(title);
            dashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
            dashboardsTitle1.add(doPost("/api/customer/" + customerId.getId().toString() 
                    + "/dashboard/" + dashboard.getId().getId().toString(), Dashboard.class));
        }
        String title2 = "Dashboard title 2";
        List<Dashboard> dashboardsTitle2 = new ArrayList<>();
        for (int i=0;i<143;i++) {
            Dashboard dashboard = new Dashboard();
            String suffix = RandomStringUtils.randomAlphanumeric((int)(Math.random()*15));
            String title = title2+suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            dashboard.setTitle(title);
            dashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
            dashboardsTitle2.add(doPost("/api/customer/" + customerId.getId().toString() 
                    + "/dashboard/" + dashboard.getId().getId().toString(), Dashboard.class));
        }
        
        List<Dashboard> loadedDashboardsTitle1 = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(18, title1);
        TextPageData<Dashboard> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/dashboards?", 
                    new TypeReference<TextPageData<Dashboard>>(){}, pageLink);
            loadedDashboardsTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());
        
        Collections.sort(dashboardsTitle1, idComparator);
        Collections.sort(loadedDashboardsTitle1, idComparator);
        
        Assert.assertEquals(dashboardsTitle1, loadedDashboardsTitle1);
        
        List<Dashboard> loadedDashboardsTitle2 = new ArrayList<>();
        pageLink = new TextPageLink(7, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/dashboards?", 
                    new TypeReference<TextPageData<Dashboard>>(){}, pageLink);
            loadedDashboardsTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(dashboardsTitle2, idComparator);
        Collections.sort(loadedDashboardsTitle2, idComparator);
        
        Assert.assertEquals(dashboardsTitle2, loadedDashboardsTitle2);
        
        for (Dashboard dashboard : loadedDashboardsTitle1) {
            doDelete("/api/customer/dashboard/" + dashboard.getId().getId().toString())
            .andExpect(status().isOk());
        }
        
        pageLink = new TextPageLink(5, title1);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/dashboards?", 
                new TypeReference<TextPageData<Dashboard>>(){}, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
        
        for (Dashboard dashboard : loadedDashboardsTitle2) {
            doDelete("/api/customer/dashboard/" + dashboard.getId().getId().toString())
            .andExpect(status().isOk());
        }
        
        pageLink = new TextPageLink(9, title2);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/dashboards?", 
                new TypeReference<TextPageData<Dashboard>>(){}, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

}