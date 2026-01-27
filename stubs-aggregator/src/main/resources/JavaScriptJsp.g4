grammar JavaScriptJsp;

@header{
package org.qubership.automation.itf.ui.service.javaScriptParser;

import java.util.*;
}

@lexer::members {
  @Override
  public void reportError(RecognitionException e) {}
}

variableUsages returns[List<String> usedVariablesList]
@init{
    $usedVariablesList = new ArrayList<String>();
}
: (variableUsage{
    if(!$usedVariablesList.contains($variableUsage.text)){
        $usedVariablesList.add($variableUsage.text);
    }
})+?;

variableUsage returns[String variableUsageStr]: VariableUsage {$variableUsageStr = $VariableUsage.text.replace("<%=", "").replace("%>", "");};

ifUsages returns[List<String> ifUsagesList]
@init{
    $ifUsagesList = new ArrayList<String>();
}
:
(ifUsage{
    $ifUsagesList.add($ifUsage.text);
})+?
;

ifUsage returns[String ifUsageStr]
:
IfUsage {$ifUsageStr = $IfUsage.text.replace("<%", "").replace("%>", "").replace("{", "").trim();}
;

forUsages returns[List<String> forUsagesList]
@init{
    $forUsagesList = new ArrayList<String>();
}
:
(forUsage{
    $forUsagesList.add($forUsage.text);
})+?
;

forUsage returns[String forUsageStr]
:
ForUsage {$forUsageStr = $ForUsage.text.replace("<%", "").replace("%>", "").replace("{", "").trim();}
;

IfUsage
:
'<%' (' ')* 'if' (' ')* ConditionPart (' ')* ('{')?(' ')* (.)*? (' ')* '%>'
;

ForUsage
:
'<%' (' ')* 'for' (' ')* ConditionPart (' ')* ('{')?(' ')* (.)*? (' ')* '%>'
;

ConditionPart
:
'(' (' ')* VariableName (' ')* ')'
;

VariableUsage
:
'<%='VariableName'%>'
;

fragment VariableName
:
(.)+?
;
