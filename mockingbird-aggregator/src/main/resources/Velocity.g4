grammar Velocity;

@header{
   import java.util.*;
}

methodCalls returns[List<String> methodCallsList]
@init{
    $methodCallsList = new ArrayList<String>();
}
: (methodCall {
    if(!$methodCallsList.contains($methodCall.text)) $methodCallsList.add($methodCall.text);
})*;

methodCall returns[String methodCallStr]
: MethodCallRule {$methodCallStr = $MethodCallRule.text};

variables returns[List<String> variablesList]
@init {
    $variablesList = new ArrayList<String>();
}
:
(variable {
    if(!$variablesList.contains($variable.text)) $variablesList.add($variable.text);
})*
;

variable returns[String varName] : Variable {$varName = $Variable.text};

Variable: (VARLINK)(OPTIONAL)?('{'VariableRef'}'|VariableRef);

MethodCallRule
:
(VARLINK)(OPTIONAL)?'{'MethodCall'}'
|(VARLINK)(OPTIONAL)?MethodCall
|(VARLINK)(OPTIONAL)?VariableRef'('MethodCallRule')';

MethodCall
:VariableRef'('Variable')'
|VariableRef'('')'
;

fragment VariableRef: Identifier((DELIMITER)?Namepart)*;

fragment Identifier: (IDENTIFIER)+(Namepart)*;

fragment VARLINK: '$';

fragment LETTER: [a-zA-Z];

fragment OPTIONAL: '!';

fragment DELIMITER: '.';

fragment WHITESPACE: '_';

fragment DIGIT: [0-9];

fragment IDENTIFIER: LETTER|WHITESPACE;

fragment Namepart: (IDENTIFIER|DIGIT)+;
