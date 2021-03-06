//--- BEGIN COPYRIGHT BLOCK ---
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; version 2 of the License.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License along
//with this program; if not, write to the Free Software Foundation, Inc.,
//51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//(C) 2013 Red Hat, Inc.
//All rights reserved.
//--- END COPYRIGHT BLOCK ---
package com.netscape.certsrv.selftests;

import java.net.URISyntaxException;

import javax.ws.rs.core.Response;

import com.netscape.certsrv.client.Client;
import com.netscape.certsrv.client.PKIClient;

/**
 * @author Endi S. Dewata
 */
public class SelfTestClient extends Client {

    public SelfTestResource resource;

    public SelfTestClient(PKIClient client, String subsystem) throws URISyntaxException {
        super(client, subsystem, "selftest");
        init();
    }

    public void init() throws URISyntaxException {
        resource = createProxy(SelfTestResource.class);
    }

    public SelfTestCollection findSelfTests(String filter, Integer start, Integer size) {
        Response response = resource.findSelfTests(filter, start, size);
        return client.getEntity(response, SelfTestCollection.class);
    }

    public void executeSelfTests(String action) {
        Response response = resource.executeSelfTests(action);
        client.getEntity(response, Void.class);
    }

    public SelfTestData getSelfTest(String selfTestID) {
        Response response = resource.getSelfTest(selfTestID);
        return client.getEntity(response, SelfTestData.class);
    }
}
