#summary Continuous Delivery with Jenkins and rundeck Lab for team '${infrastructure.identifier}'

*<font size="5">Continuous Delivery with Jenkins and rundeck Lab for Team '${infrastructure.identifier}'</font>*

<wiki:toc max_depth="1" />

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

= Environment setup =

 # Get the SSH private key [https://s3-eu-west-1.amazonaws.com/continuous-delivery/continuous-delivery-workshop.pem continuous-delivery-workshop.pem] to connect to the servers
 {{{
mkdir ~/.aws/
curl https://s3-eu-west-1.amazonaws.com/continuous-delivery/continuous-delivery-workshop.pem --output ~/.aws/continuous-delivery-workshop.pem
chmod 400 ~/.aws/continuous-delivery-workshop.pem
}}}
 # Clone Github repository ${infrastructure.githubRepositoryName}
  {{{
mkdir ~/continuous-delivery-workshop
cd ~/continuous-delivery-workshop
git clone ${infrastructure.githubRepositoryCloneUrl}
}}}
  Note: password of the "${infrastructure.gitHubAccountUsername}" user will be sent before the workshop.
 # build project
 {{{
cd ~/continuous-delivery-workshop/${infrastructure.githubRepositoryName}
mvn package
}}}
 # Modify the welcome page and push the change
  ## Modify {{{~/continuous-delivery-workshop/${infrastructure.githubRepositoryName}/src/main/webapp/welcome.jsp}}}
  ## Push the change
   {{{
${infrastructure.githubRepositoryName} > git commit -m "test" src/main/webapp/welcome.jsp
${infrastructure.githubRepositoryName} > git push
}}}
 # Verify that Jenkins detects the Git change and triggers a build : [${infrastructure.jenkinsUrl}/job/${infrastructure.githubRepositoryName}/] (it may take up to 1 minute).
 # Do a release of the application
  ## Add the Nexus credentials to your Maven settings.xml file :
   {{{
<settings>
  <servers>
    <!-- Xebia Workshop -->
    <server>
      <id>xebia-tech-event-nexus-releases</id>
      <username>deployment</username>
      <password>deployment123</password>
    </server>
    <server>
      <id>xebia-tech-event-nexus-snapshots</id>
      <username>deployment</username>
      <password>deployment123</password>
    </server>
  </servers>
</settings>
}}}
  ## Execute {{{mvn release:prepare -B release:perform}}}
 # Verify that the new release is available in Nexus : [${infrastructure.nexusUrl}content/groups/public/fr/xebia/demo/petclinic-${infrastructure.identifier}/xebia-petclinic-lite/]
----

Links to the different labs :
<#list generatedWikiPageNames as x>
 * [${x}]
</#list>  

_${generator}_
