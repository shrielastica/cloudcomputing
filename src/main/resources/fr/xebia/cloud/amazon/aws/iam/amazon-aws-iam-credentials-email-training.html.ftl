<html>
<body>
<p>
Hello, <br/>
<br/>
Here are the credentials to connect to Xebia Amazon AWS/EC2 training infrastructure. <br/>
<br/>
Don't hesitate to connect to Amazon AWS, to play with it but please <strong>do not forget to stop instances or if possible terminate them after using them</strong>.
Letting instances started would cost unnecessary money to Xebia.
</p>

<h2>User Sign-In Credentials</h2>
<p>
Authentication page <a href="https://xebia-france.signin.aws.amazon.com/console">https://xebia-france.signin.aws.amazon.com/console</a>.
</p>
<ul>
<li>User Name: ${loginUserName}</li>
<li>Password: ${loginPassword}</li>
</ul>

<h2>User Access Credentials</h2/>

<h3>Access Keys</h3>

<ul>
<li>Access Key Id: ${accessKeyId}</li>
<li>Secret Access Key:  ${accessKeySecretId}</li>
</ul>

<#if attachedCredentialsFileName?exists >
<p>
See attached "${attachedCredentialsFileName}".
</p>
</#if>

<h3>Signing Certificate</h3>

<ul>
<li>Certificate Id: ${x509CertificateId}</li>
</ul>

<p>
<#if attachedX509PrivateKeyFileName?exists >
See attached "${attachedX509PrivateKeyFileName}" and "${attachedX509CertificateFileName}".
<#else>
The certificate and its private key have already been generated and sent.
</#if>
</p>

<h2>Server Connection Credentials / SSH key</h2>

<ul>
<li>SSH key name: ${sshKeyName}</li>
<li>SSH key finger print: ${sshKeyFingerprint}</li>
</ul>

<p>
<#if attachedSshKeyFileName?exists >
See attached "${attachedSshKeyFileName}".
<#else>
The SSH key and its private key have already been generated and sent.
</#if> 
</p>

<p>
Thanks,<br/>
<br/>
Cyrille
</p>
</body>
</html>