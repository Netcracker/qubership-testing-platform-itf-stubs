grammar VelocityVariables;

@header{
package org.qubership.automation.itf.ui.service;
import java.util.*;
}

variables returns[List<String> variablesList]
@init {
    $variablesList = new ArrayList<String>();
}
@after {
    System.out.println($variablesList.size() + " variables parsed");
}
:
(variable {
    if(!$variablesList.contains($variable.text)) $variablesList.add($variable.text);
})*
;

variable returns[String varName] : Variable {$varName = $Variable.text};

Variable: (VARLINK)(OPTIONAL)?('{'VariableRef'}'|VariableRef);

fragment VariableRef: (IDENTIFIER)+(Namepart)*((DELIMITER)?Namepart)*;

fragment VARLINK: '$';

fragment LETTER: [a-zA-Z];

fragment OPTIONAL: '!';

fragment DELIMITER: '.';

fragment WHITESPACE: '_';

fragment DIGIT: [0-9];

fragment IDENTIFIER: LETTER|WHITESPACE;

fragment Namepart: (IDENTIFIER|DIGIT)+;
