#!/usr/bin/python -t
# Authors:
#     Matthew Harmsen <mharmsen@redhat.com>
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; version 2 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along
# with this program; if not, write to the Free Software Foundation, Inc.,
# 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Copyright (C) 2012 Red Hat, Inc.
# All rights reserved.
#

# System Imports
from __future__ import absolute_import
import abc
import six


# PKI Deployment Abstract Base PKI Scriptlet
class AbstractBasePkiScriptlet(six.with_metaclass(abc.ABCMeta, object)):
    @abc.abstractmethod
    def spawn(self, deployer):
        """Retrieve data from the specified PKI dictionary and
           use it to install a new PKI instance."""
        return

    # pylint: disable=W0613
    @abc.abstractmethod
    def destroy(self, deployer):
        """Retrieve data from the specified PKI dictionary and
           use it to destroy an existing PKI instance."""
        return
