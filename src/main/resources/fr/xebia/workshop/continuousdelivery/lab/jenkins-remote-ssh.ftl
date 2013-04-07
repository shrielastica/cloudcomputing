#summary Continuous Delivery with Jenkins and rundeck Lab for team '${infrastructure.identifier}'

*<font size="5">Continuous Delivery with Jenkins SSH Remote Plugin for Team '${infrastructure.identifier}'</font>*

= Your architecture =

<img width="400" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/per-team-infrastructure.png" />

<table>
<tr><td> *SSH Private key* </td><td> [https://s3-eu-west-1.amazonaws.com/continuous-delivery/continuous-delivery-workshop.pem continuous-delivery-workshop.pem]</td></tr>
<tr><td> *!GitHub project repository url* </td><td> [${infrastructure.githubRepositoryHomePageUrl}] </td></tr>
<tr><td> *Jenkins URL* </td><td> [${infrastructure.jenkinsUrl}] </td></tr>
<tr><td> *Rundeck URL* </td><td> [${infrastructure.rundeckUrl}] </td></tr>
<tr><td> *Deployit URL* </td><td> [${infrastructure.deployitUrl}] </td></tr>
<tr><td> *Nexus URL* </td><td> [${infrastructure.nexusUrl}] </td></tr>
<tr><td> *Tomcat Dev URL* </td><td> [${infrastructure.devTomcatUrl}] </td></tr>
<tr><td> *Tomcat Dev SSH* </td><td> 
{{{
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.devTomcat.publicDnsName}
}}}
 </td></tr>
<tr><td> *Tomcat Valid 1 URL* </td><td> [${infrastructure.validTomcat1Url}] </td></tr>
<tr><td> *Tomcat Valid 1 SSH* </td><td> 
{{{
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.validTomcat1.publicDnsName}
}}}
 </td></tr>
<tr><td> *Tomcat Valid 2 URL* </td><td> [${infrastructure.validTomcat2Url}] </td></tr>
<tr><td> *Tomcat Valid 2 SSH* </td><td> 
{{{
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.validTomcat2.publicDnsName}
}}}
 </td></tr>
</table>

----

= Lab: 'Local' Scripted Tomcat Deployment =

*Goal:* Develop a script to deploy a new version of '{{{xebia-petclinic-lite.war}}}' on a tomcat server.

== Script Specifications ==
This script must:
 # Be installed under {{{$TOMCAT_HOME/bin/tomcat-deploy-petclinic}}} (use a [http://en.wikipedia.org/wiki/Shebang_(Unix) Unix Shebang] instead of suffixing your script by ".sh", ".py", etc)
 # Stop the Tomcat server if it is running
 # Delete existing {{{$TOMCAT_HOME/webapps/xebia-petclinic-lite.war}}} file if exists,
 # Delete existing {{{$TOMCAT_HOME/webapps/xebia-petclinic-lite}}} folder if exists,
 # Download latest version of {{{xebia-petclinic-lite.war}}} (groupId=${infrastructure.projectMavenGroupId}, artifactId=${infrastructure.projectMavenArtifactId}) and copy it to {{{$TOMCAT_HOME/webapps/}}}
  ** {{{xebia-petclinic-lite.war}}} is deployed on [${infrastructure.nexusUrl}content/groups/public/] but it is more elegant to use Nexus REST API (see below)
 # Start Tomcat server
 # Go further in the lab: test that the URL http://localhost:8080/xebia-petclinic-lite/ returns "200 OK"
 
Answer : [http://xebia-france.googlecode.com/svn/cloudcomputing/xebia-cloudcomputing-extras/trunk/src/main/scripts/fr/xebia/workshop/continuousdelivery/tomcat-deploy-petclinic tomcat-deploy-petclinic]

== Tomcat scripting tips and tricks ==

=== Unix shebang ===

 * shell shebang (equivalent to ".sh"):
  {{{
#!/usr/bin/env sh
}}}
 * python shebang (equivalent to ".py"):
  {{{
#!/usr/bin/env python -c
}}}

=== What is {{{$TOMCAT_HOME}}} for this workshop installation === 

{{{
$TOMCAT_HOME=/opt/tomcat/apache-tomcat-6/
}}}

=== How to stop and start Tomcat 6  ===

<table>
<tr><td> *Start* </td><td>
{{{
$tomcat> /opt/tomcat/apache-tomcat-6/bin/catalina.sh start
}}}
</td></tr>
<tr><td> *Stop* </td><td>
{{{
$tomcat> /opt/tomcat/apache-tomcat-6/bin/catalina.sh stop
}}}
</td></tr>
</table>

=== Nexus REST API ===

 * Nexus REST API [https://repository.sonatype.org/nexus-core-documentation-plugin/core/docs/rest.artifact.maven.content.html /artifact/maven/content]:
  ** Pseudo code:
   {{{
http://nexus.aws.xebia-tech-event.info:8081/nexus/service/local/artifact/maven/content?
   g=$GROUP_ID&
   a=$ARTIFACT_ID&
   r=$REPOSITORY&
   v=$VERSION&
   e=war
}}}
  ** g: Group id of the artifact (Required).
  ** a: Artifact id of the artifact (Required).
  ** v: Version of the artifact (Required) Supports resolving of "LATEST", "RELEASE" and snapshot versions ("1.0-SNAPSHOT") too.
  ** r: Repository that the artifact is contained in (Required).	E.g.: "{{{releases}}}" or "{{{snapshots}}}".
  ** p: Packaging type of the artifact (Optional).
  ** c: Classifier of the artifact (Optional).	
  ** e: Extension of the artifact (Optional).
 * To go further in the lab, Nexus REST API [https://repository.sonatype.org/nexus-core-documentation-plugin/core/docs/rest.artifact.maven.resolve.html /artifact/maven/resolve]
 
=== How to specify the name of the downloaded artifact with curl and wget ===

{{{
curl http://example.com/test.txt --output /tmp/renamed-test.txt
wget http://example.com/test.txt --output-document /tmp/renamed-test.txt
}}}

=== How to test existence of a file, of a folder ===
{{{
if [ -f /tmp/test.txt ];
then
   echo "File '/tmp/test.txt' exists"
fi
if [ -d /tmp/test-folder ];
then
   echo "Folder '/tmp/test-folder' exists"
fi
}}}

=== How to verify a http response code  ===

{{{
HEALT_CHECK_URL="http://www.iana.org/domains/example/"
HEALTH_CHECK_HTTP_CODE=$(curl --connect-timeout 10 --retry 10 --silent --show-error -w "%{http_code}" -o /dev/null http://www.iana.org/domains/example/) 
if [ $HEALTH_CHECK_HTTP_CODE -eq 200 ];
then
   echo "Health check url $HEALT_CHECK_URL returned expected http code '$HEALTH_CHECK_HTTP_CODE'"
else
   echo "FAILURE: '$HEALT_CHECK_URL' is KO (returned '$HEALTH_CHECK_HTTP_CODE')"
fi
}}}

=== SCP connection parameters to Tomcat servers ===

SCP command lines to copy a local "{{{tomcat-deploy-petclinic}}}" to the remote tomcat servers, and SSH command lines to make it executable.
<table>
<tr><td> *Tomcat Dev SSH* </td><td> 
{{{
scp -i ~/.aws/continuous-delivery-workshop.pem tomcat-deploy-petclinic tomcat@${infrastructure.devTomcat.publicDnsName}:/opt/tomcat/apache-tomcat-6/bin/
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.devTomcat.publicDnsName} chmod a+x /opt/tomcat/apache-tomcat-6/bin/tomcat-deploy-petclinic
}}}
 </td></tr>
<tr><td> *Tomcat Valid 1 SSH* </td><td> 
{{{
scp -i ~/.aws/continuous-delivery-workshop.pem tomcat-deploy-petclinic tomcat@${infrastructure.validTomcat1.publicDnsName}:/opt/tomcat/apache-tomcat-6/bin/
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.validTomcat1.publicDnsName} chmod a+x /opt/tomcat/apache-tomcat-6/bin/tomcat-deploy-petclinic
}}}
 </td></tr>
<tr><td> *Tomcat Valid 2 SSH* </td><td> 
{{{
scp -i ~/.aws/continuous-delivery-workshop.pem tomcat-deploy-petclinic tomcat@${infrastructure.validTomcat2.publicDnsName}:/opt/tomcat/apache-tomcat-6/bin/
ssh -i ~/.aws/continuous-delivery-workshop.pem tomcat@${infrastructure.validTomcat2.publicDnsName} chmod a+x /opt/tomcat/apache-tomcat-6/bin/tomcat-deploy-petclinic
}}}
</td></tr>
<table>

----

= Lab. Automated Tomcat Deployment with Jenkins SSH Plugin = 

*Goal:* Deploy a new version of '{{{xebia-petclinic-lite.war}}}' on the "Tomcat Dev server" at the end of your build with the "Jenkins SSH Plugin".

== Architecture ==

<img width="400" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/jenkins-ssh-plugin.png"/>

== Lab ==

Note: the [https://wiki.jenkins-ci.org/display/JENKINS/SSH+plugin Jenkins SSH Plugin] is already installed on your jenkins server.
 # Deploy the "[http://xebia-france.googlecode.com/svn/cloudcomputing/xebia-cloudcomputing-extras/trunk/src/main/scripts/fr/xebia/workshop/continuousdelivery/tomcat-deploy-petclinic tomcat-deploy-petclinic]" shell script on the Tomcat Dev server.
 # Connect to your *Jenkins* server: [${infrastructure.jenkinsUrl}]
 # In the *Manage Jenkins* / *Configure System*
  <img height="20" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/jenkins-manage-jenkins-screenshot.png" /> <img height="25" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/jenkins-configure-system-screenshot.png" /> 
 # In the section called : *SSH Remote hosts*, declare your "Tomcat Dev" server:
  ** Host: {{{${infrastructure.devTomcat.publicDnsName}}}}
  ** Port: #blank#
  ** Username: {{{tomcat}}}
  ** Password: #blank#
  ** Keyfile: {{{/var/lib/jenkins/.ssh/continuous-delivery-workshop.pem}}}
   <img height="135" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/jenkins-add-ssh-remote-host-screenshot.png" />
 # *Save* the settings
 # Create a new Jenkins *Free Style Project* named "{{{deploy-petclinic-on-tomcat-dev}}}" with the following parameters:
   <img height="75" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/jenkins-create-free-style-project.png" />
 # Activate option called *Execute shell script on remote host using SSH*
 # Select the right SSH site "{{{${infrastructure.devTomcat.publicDnsName}}}}"
 # In *post-build script*, enter 
  {{{
  /opt/tomcat/apache-tomcat-6/bin/tomcat-deploy-petclinic ${infrastructure.projectMavenGroupId}
}}}
   <img height="80" src="http://xebia-france.googlecode.com/svn/wiki/cont-delivery-img/jenkins-configure-job-ssh-post-build-script-screenshot.png" />
   Note: the provided _answer_ [http://xebia-france.googlecode.com/svn/cloudcomputing/xebia-cloudcomputing-extras/trunk/src/main/scripts/fr/xebia/workshop/continuousdelivery/tomcat-deploy-petclinic tomcat-deploy-petclinic] needs the maven group id as input parameter ; you may have hardcoded the project maven group id "${infrastructure.projectMavenGroupId}" in the script you developped.

----
_${generator}_
