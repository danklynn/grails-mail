/*
 * Copyright 2008 the original author or authors.
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
package org.grails.mail

import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.MimeMessage
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.springframework.mail.MailSender
import org.springframework.mail.javamail.JavaMailSender
import javax.mail.internet.MimeMultipart
import org.springframework.mail.javamail.MimeMessagePreparator
import org.springframework.mail.SimpleMailMessage

/**
 * Test case for {@link MailMessageBuilder}.
 */
class MailMessageBuilderTests extends GroovyTestCase {
    def mockService
    def mockSender
    def testBuilder

    void setUp() {
        super.setUp()

        ConfigurationHolder.config = new ConfigObject()
        mockService = [:] as MailService
        mockSender = new MockJavaMailSender()
        testBuilder = new MailMessageBuilder(mockService, mockSender)
    }

    void tearDown() {
        ConfigurationHolder.config = null
        super.tearDown()
    }

	void testStreamCharBufferForGrails12() {
		if(grails.util.GrailsUtil.grailsVersion.startsWith("1.2")) {
			processDsl {
	            to "fred@g2one.com"
	            subject "Hello Fred"
	
				def text = getClass().classLoader.loadClass("org.codehaus.groovy.grails.web.util.StreamCharBuffer").newInstance()
				text.writer << 'How are you?'
	            body text
	        }		

	        def msg = testBuilder.createMessage().mimeMessage
	        assertEquals 1, to(msg).size()
	        assertEquals "fred@g2one.com", to(msg)[0].toString()
	        assertEquals "Hello Fred", msg.subject
	        assertEquals "How are you?", msg.content
	        
		}
	}
    /**
     * Tests the basic elements of the mail DSL.
     */
    void testBasics() {
        processDsl {
            to "fred@g2one.com"
            subject "Hello Fred"
            body 'How are you?'
        }

        def msg = testBuilder.createMessage().mimeMessage
        assertEquals 1, to(msg).size()
        assertEquals "fred@g2one.com", to(msg)[0].toString()
        assertEquals "Hello Fred", msg.subject
        assertEquals "How are you?", msg.content
    }

    /**
     * Tests that multiple recipients are added to the underlying mail
     * message correctly.
     */
    void testMultipleRecipients() {
        processDsl {
            to "fred@g2one.com","ginger@g2one.com", "grace@hollywood.com"
            from "john@g2one.com"
            cc "marge@g2one.com", "ed@g2one.com"
            bcc "joe@g2one.com"
            subject "Hello John"
            body 'this is some text'
        }

        def msg = testBuilder.createMessage().mimeMessage
        assertEquals([ "fred@g2one.com", "ginger@g2one.com", "grace@hollywood.com" ], to(msg))
        assertEquals([ "marge@g2one.com", "ed@g2one.com" ], cc(msg))
        assertEquals([ "joe@g2one.com" ], bcc(msg))
        assertEquals 1, msg.from.size()
        assertEquals "john@g2one.com", msg.from[0].toString()
        assertEquals "Hello John", msg.subject
        assertEquals "this is some text", msg.content
    }

    /**
     * Tests the "headers" feature of the mail DSL. It should add the
     * specified headers to the underlying MIME message.
     */
    void testHeaders() {
        processDsl {
            headers "X-Mailing-List": "user@grails.codehaus.org",
                    "Sender": "dilbert@somewhere.org"
            to "fred@g2one.com"
            subject "Hello Fred"
            body 'How are you?'
        }

        def msg = testBuilder.createMessage().mimeMessage
        assertEquals "user@grails.codehaus.org", msg.getHeader("X-Mailing-List", ", ")
        assertEquals "dilbert@somewhere.org", msg.getHeader("Sender", ", ")
        assertEquals([ "fred@g2one.com" ], to(msg))
        assertEquals "Hello Fred", msg.subject
        assertEquals "How are you?", msg.content
    }

    /**
     * Tests that the builder throws an exception if the user tries to
     * specify custom headers with just a plain MailSender.
     */
    void testHeadersWithBasicMailSender() {
        mockSender = [:] as MailSender
        testBuilder = new MailMessageBuilder(mockService, mockSender)

        shouldFail(GrailsMailException) {
            processDsl {
                headers "Content-Type": "text/plain;charset=UTF-8",
                        "Sender": "dilbert@somewhere.org"
                to "fred@g2one.com"
                subject "Hello Fred"
                body 'How are you?'
            }
        }
    }

    void testAttachment() {
        processDsl {
            multipart true
            to "fred@g2one.com"
            subject "Hello Fred"
            body 'How are you?'
            attachBytes "dummy.bin", "application/binary", "abcdef".bytes
        }
        def msg = testBuilder.createMessage().mimeMessage
        assertTrue msg.content instanceof MimeMultipart
        assertEquals 2, msg.content.count

        def attachment = msg.content.getBodyPart(1)
        assertEquals "abcdef", attachment.content.text
        assertEquals "dummy.bin", attachment.fileName

        assert msg.content.getBodyPart(0).content == 'How are you?'
    }

    void testHtmlContentType() {
        processDsl {
            html '<html><head></head><body>How are you?</body></html>'
        }

        def msg = reparseMessage(testBuilder.createMessage().mimeMessage)

        assert msg.contentType == 'text/html; charset=UTF-8'
        assert msg.content == '<html><head></head><body>How are you?</body></html>'
    }

    void testMultipart_html_first() {
        processDsl {
            multipart true
            html '<html><head></head><body>How are you?</body></html>'
            text 'How are you?'
        }

        def msg = testBuilder.createMessage().mimeMessage

        assert msg.content instanceof MimeMultipart
        MimeMultipart mp = msg.content
        assert mp.count == 2
        assert mp.getBodyPart(0).contentType.startsWith('text/html')
        assert mp.getBodyPart(0).content == '<html><head></head><body>How are you?</body></html>'

        assert mp.getBodyPart(1).contentType.startsWith('text/plain')
        assert mp.getBodyPart(1).content == 'How are you?'
    }

    void testMultipart_text_first() {
        processDsl {
            multipart true
            text 'How are you?'
            html '<html><head></head><body>How are you?</body></html>'
        }

        def msg = testBuilder.createMessage().mimeMessage

        assert msg.content instanceof MimeMultipart
        MimeMultipart mp = msg.content
        assert mp.count == 2
        assert mp.getBodyPart(0).contentType.startsWith('text/html')
        assert mp.getBodyPart(0).content == '<html><head></head><body>How are you?</body></html>'

        assert mp.getBodyPart(1).contentType.startsWith('text/plain')
        assert mp.getBodyPart(1).content == 'How are you?'
    }

    /**
     * The original MimeMessage#getContentType() will always return 'text/plain', even though the
     * message writes to a stream correctly. This simply writes the given message to a byte array and reads
     * it back into a new message object.
     */
    private MimeMessage reparseMessage(MimeMessage msg) {
        def stream = new ByteArrayOutputStream()
        msg.writeTo stream
        def realMessage = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(stream.toByteArray()))
        return realMessage
    }

    private List to(MimeMessage msg) {
        msg.getRecipients(Message.RecipientType.TO)*.toString()
    }

    private List cc(MimeMessage msg) {
        msg.getRecipients(Message.RecipientType.CC)*.toString()
    }

    private List bcc(MimeMessage msg) {
        msg.getRecipients(Message.RecipientType.BCC)*.toString()
    }

    private processDsl(Closure c) {
        c.delegate = this.testBuilder
        c.call()
    }
}


class MockJavaMailSender implements JavaMailSender {

    MimeMessage createMimeMessage() {
        new MimeMessage(Session.getInstance(new Properties()))
    }

    MimeMessage createMimeMessage(InputStream inputStream) {
        new MimeMessage(Session.getInstance(new Properties()), inputStream)
    }

    void send(MimeMessage mimeMessage) {
        throw new UnsupportedOperationException()
    }

    void send(MimeMessage[] mimeMessages) {
        throw new UnsupportedOperationException()
    }

    void send(MimeMessagePreparator mimeMessagePreparator) {
        throw new UnsupportedOperationException()
    }

    void send(MimeMessagePreparator[] mimeMessagePreparators) {
        throw new UnsupportedOperationException()
    }

    void send(SimpleMailMessage simpleMailMessage) {
        throw new UnsupportedOperationException()
    }

    void send(SimpleMailMessage[] simpleMailMessages) {
        throw new UnsupportedOperationException()
    }
}