#!/bin/bash
# vim: dict=/usr/share/beakerlib/dictionary.vim cpt=.,w,b,u,t,i,k
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#
#   runtest.sh of /CoreOS/dogtag/acceptance/cli-tests/pki-user-cli
#   Description: PKI user-add CLI tests
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# The following pki cli commands needs to be tested:
#  pki-user-cli-user-add    Add users to pki subsystems.
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#
#   Author: Asha Akkiangady <aakkiang@redhat.com>
#
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#
#   Copyright (c) 2013 Red Hat, Inc. All rights reserved.
#
#   This copyrighted material is made available to anyone wishing
#   to use, modify, copy, or redistribute it subject to the terms
#   and conditions of the GNU General Public License version 2.
#
#   This program is distributed in the hope that it will be
#   useful, but WITHOUT ANY WARRANTY; without even the implied
#   warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
#   PURPOSE. See the GNU General Public License for more details.
#
#   You should have received a copy of the GNU General Public
#   License along with this program; if not, write to the Free
#   Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
#   Boston, MA 02110-1301, USA.
#
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# Include rhts environment
. /usr/bin/rhts-environment.sh
. /usr/share/beakerlib/beakerlib.sh
. /opt/rhqa_pki/rhcs-shared.sh
. /opt/rhqa_pki/pki-cert-cli-lib.sh
. /opt/rhqa_pki/env.sh


########################################################################
# Test Suite Globals
########################################################################

run_pki-user-cli-user-del-ocsp_tests(){
    rlPhaseStartSetup "pki_user_cli_user_add-ocsp-startup:Getting nss certificate db "
	 rlLog "Certificate directory = $CERTDB_DIR"
    rlPhaseEnd

    rlPhaseStartCleanup "pki_user_cli_user_add-cleanup: Delete temp dir"
	del_user=($OCSP_adminV_user $OCSP_adminR_user $OCSP_adminE_user $OCSP_adminUTOCSP_user $OCSP_agentV_user $OCSP_agentR_user $OCSP_agentE_user $OCSP_agentUTOCSP_user $OCSP_auditV_user $OCSP_operatorV_user)

	#===Deleting users created using OCSP_adminV cert===#
	i=1
	while [ $i -lt 25 ] ; do
               rlRun "pki -d $CERTDB_DIR \
                          -n OCSP_adminV \
                          -c $CERTDB_DIR_PASSWORD \
                           user-del  u$i > $TmpDir/pki-user-del-ocsp-user-00$i.out" \
                           0 \
                           "Deleted user  u$i"
		rlAssertGrep "Deleted user \"u$i\"" "$TmpDir/pki-user-del-ocsp-user-00$i.out"
                let i=$i+1
        done
        #===Deleting users(symbols) created using OCSP_adminV cert===#
	j=1
        while [ $j -lt 8 ] ; do
	       eval usr=\$user$j
               rlRun "pki -d $CERTDB_DIR \
                          -n OCSP_adminV \
                          -c $CERTDB_DIR_PASSWORD \
                           user-del  $usr > $TmpDir/pki-user-del-ocsp-user-symbol-00$j.out" \
                           0 \
                           "Deleted user $usr"
                rlAssertGrep "Deleted user \"$usr\"" "$TmpDir/pki-user-del-ocsp-user-symbol-00$j.out"
                let j=$j+1
        done
	i=0
        while [ $i -lt ${#del_user[@]} ] ; do
               userid_del=${del_user[$i]}
               rlRun "pki -d $CERTDB_DIR \
                          -n \"$admin_cert_nickname\" \
                          -c $CERTDB_DIR_PASSWORD \
                           user-del  $userid_del > $TmpDir/pki-user-del-ocsp-00$i.out"  \
                           0 \
                           "Deleted user  $userid_del"
                rlAssertGrep "Deleted user \"$userid_del\"" "$TmpDir/pki-user-del-ocsp-00$i.out"
                let i=$i+1
        done

    rlPhaseEnd
}
