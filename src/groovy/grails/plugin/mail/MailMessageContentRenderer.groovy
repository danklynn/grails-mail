/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugin.mail

import grails.util.GrailsWebUtil

import org.codehaus.groovy.grails.plugins.PluginManagerHolder

import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.i18n.FixedLocaleResolver

import org.springframework.context.ApplicationContext

import org.apache.commons.logging.LogFactory

import javax.servlet.http.HttpServletRequest

/**
 * Responsible for rendering a GSP into the content of a mail message.
 */
class MailMessageContentRenderer {

    static PATH_TO_MAILVIEWS = "/WEB-INF/grails-app/views"
    
    private log = LogFactory.getLog(MailMessageContentRenderer)
    
    def groovyPagesTemplateEngine
    def groovyPagesUriService
    def grailsApplication
        
    MailMessageContentRender render(Writer out, templateName, model, locale, pluginName = null) {
        RenderEnvironment.with(grailsApplication.mainContext, out, locale) { env ->
            def template = createTemplate(templateName, env.controllerName, pluginName)
            if (model instanceof Map) {
                template.make(model).writeTo(out)
            } else {
                template.make().writeTo(out)
            }
            
            new MailMessageContentRender(out, template.metaInfo.contentType)
        }
    }

    protected createTemplate(String templateName, String controllerName, String pluginName) {
        if (templateName.startsWith("/")) {
            if (!controllerName) {
                controllerName = ""
            }
        } else {
            if (!controllerName) {
                throw new IllegalArgumentException("Mail views cannot be loaded from relative view paths where there is no current HTTP request")
            }
        }
        
        def contextPath = getContextPath(pluginName)
        def templateUri = groovyPagesUriService.getDeployedViewURI(controllerName, templateName)
        def uris = ["$contextPath$templateUri", "$contextPath/grails-app/views$templateUri"] as String[]
        def template = groovyPagesTemplateEngine.createTemplateForUri(uris)
        
        if (!template) {
            if (pluginName) {
                throw new IllegalArgumentException("Could not locate email view ${templateName} in plugin [$pluginName]")
            } else {
                throw new IllegalArgumentException("Could not locate mail body ${templateName}. Is it in a plugin? If so you must pass the plugin name in the [plugin] variable")
            }
        }
        
        template
    }
    
    protected getContextPath(pluginName) {
        def contextPath = ""
        
        if (pluginName) {
            def plugin = PluginManagerHolder.pluginManager.getGrailsPlugin(pluginName)
            if (plugin && !plugin.isBasePlugin()) {
                contextPath = plugin.pluginPath
            }
        }
        
        contextPath
    }
    
    static private class RenderEnvironment {
        final Writer out
        final Locale locale
        final ApplicationContext applicationContext
        
        private requestAttributes
        private isMock = false
        private originalOut = null
        
        RenderEnvironment(ApplicationContext applicationContext, Writer out, Locale locale = null) {
            this.out = out
            this.locale = locale
        }
        
        private init() {
            requestAttributes = RequestContextHolder.getRequestAttributes()
            
            if (!requestAttributes) {
                requestAttributes = GrailsWebUtil.bindMockWebRequest(applicationContext)
                isMock = true
            }
            
            if (locale) {
                requestAttributes.request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, new FixedLocaleResolver(defaultLocale: locale))
            }
            
            originalOut = requestAttributes.getOut()
            requestAttributes.setOut(out)
        }
        
        private close() {
            requestAttributes.setOut(originalOut)
            if (isMock) {
                RequestContextHolder.setRequestAttributes(null)
            }
        }
        
        static with(ApplicationContext applicationContext, Writer out, Locale locale, Closure block) {
            def env = new RenderEnvironment(applicationContext, out, locale)
            env.init()
            try {
                block(env)
            } finally {
                env.close()
            }
        }
        
        String getControllerName() {
            isMock ? null : requestAttributes.controllerName
        }
    }
    
}