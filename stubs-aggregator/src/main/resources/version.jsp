<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="cache-control" content="max-age=0"/>
    <meta http-equiv="cache-control" content="no-cache"/>
    <meta http-equiv="expires" content="0"/>
    <meta http-equiv="expires" content="Tue, 01 Jan 1980 1:00:00 GMT"/>
    <meta http-equiv="pragma" content="no-cache"/>
    <script src="lib/jquery/jquery-2.2.4.min.js?<%=session.getId()%>" type="text/javascript"></script>
    <script src="lib/jquery/jquery.throwable.js" type="text/javascript"></script>
    <title>Mockingbird Revision</title>
</head>
<body>
<span>Mockingbird version is: ${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.incrementalVersion}_${scmBranch}_${timestamp}_rev_${buildNumber}</span>
<br/>
<br/>
<div style="margin-top:10%;margin-left:50%;left:-70px; position:absolute; display:block; height:30px;width:350px">
    <span id="throw" style="display: none;font-size: 16pt;">I'm tired, I'll throw it away!</span>
</div>
</body>
</html>
