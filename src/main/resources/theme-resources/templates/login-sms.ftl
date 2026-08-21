<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "header">
        ${msg("smsAuthTitle",realm.displayName)}
    <#elseif section = "show-username">
        <span class="${properties.kcLoginMainFooterHelperText!}">${msg("smsAuthCodeTitle", realm.displayName)}</span>
    <#elseif section = "form">
        <form id="kc-sms-code-login-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <@field.input name="code" label=msg("smsAuthLabel") autocomplete="one-time-code" autofocus=true />
            <@buttons.actionGroup>
                <@buttons.button id="kc-login" name="login" label="doSubmit" />
            </@buttons.actionGroup>
        </form>
    <#elseif section = "info" >
        <span class="${properties.kcLoginMainFooterHelperText!}">${msg("smsAuthInstruction")}</span>
    </#if>
</@layout.registrationLayout>
