/*
 * # Copyright 2024-2025 NetCracker Technology Corporation
 * #
 * # Licensed under the Apache License, Version 2.0 (the "License");
 * # you may not use this file except in compliance with the License.
 * # You may obtain a copy of the License at
 * #
 * #      http://www.apache.org/licenses/LICENSE-2.0
 * #
 * # Unless required by applicable law or agreed to in writing, software
 * # distributed under the License is distributed on an "AS IS" BASIS,
 * # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * # See the License for the specific language governing permissions and
 * # limitations under the License.
 *
 */

package org.qubership.automation.itf.ui.config.servlets;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;

@WebServlet(urlPatterns = "/mockingbird-transport-soap-http/*",
        initParams = {
                @WebInitParam(name = "async", value = "true"),
                @WebInitParam(name = "static-resources-list",
                        value = "/mockingbird-transport-soap-http/(inbound|outbound)/(\\w|\\.)+?(js|html)"),
                @WebInitParam(name = "contextConfigLocation", value = "/WEB-INF/cxf-servlet.xml")
        })
public class CustomCxfServlet extends CXFNonSpringServlet {
    private static final long serialVersionUID = 20240822L;

    public CustomCxfServlet() {
    }

    public CustomCxfServlet(DestinationRegistry destinationRegistry) {
        super(destinationRegistry, true);
    }

    public CustomCxfServlet(DestinationRegistry destinationRegistry, boolean loadBus) {
        super(destinationRegistry, loadBus);
    }

    @Override
    public void init(ServletConfig sc) throws ServletException {
        if (getBus() == null) {
            Bus defaultBus = BusFactory.getDefaultBus();
            setBus(defaultBus != null ? defaultBus : BusFactory.newInstance().createBus());
        }
        super.init(sc);
    }
}
