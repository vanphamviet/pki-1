// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2013 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---

package com.netscape.certsrv.tps.config;

import java.util.ArrayList;
import java.util.Collection;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.jboss.resteasy.plugins.providers.atom.Link;


/**
 * @author Endi S. Dewata
 */
@XmlRootElement(name="Configurations")
public class ConfigCollection {

    Collection<ConfigData> configs = new ArrayList<ConfigData>();
    Collection<Link> links = new ArrayList<Link>();

    @XmlElementRef
    public Collection<ConfigData> getConfigs() {
        return configs;
    }

    public void setConfigs(Collection<ConfigData> configs) {
        this.configs = configs;
    }

    public void addConfig(ConfigData configData) {
        configs.add(configData);
    }

    @XmlElement(name="Link")
    public Collection<Link> getLinks() {
        return links;
    }

    public void setLink(Collection<Link> links) {
        this.links = links;
    }

    public void addLink(Link link) {
        links.add(link);
    }
}