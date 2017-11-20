package org.simplejavamail.email;

import org.simplejavamail.converter.EmailConverter;
import org.simplejavamail.converter.internal.mimemessage.MimeMessageParser;
import org.simplejavamail.internal.util.MiscUtil;

import javax.activation.DataSource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.regex.Pattern.compile;
import static org.simplejavamail.internal.util.MiscUtil.defaultTo;
import static org.simplejavamail.internal.util.MiscUtil.extractEmailAddresses;
import static org.simplejavamail.internal.util.MiscUtil.valueNullOrEmpty;
import static org.simplejavamail.internal.util.Preconditions.checkNonEmptyArgument;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_BCC_ADDRESS;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_BCC_NAME;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_BOUNCETO_ADDRESS;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_BOUNCETO_NAME;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_CC_ADDRESS;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_CC_NAME;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_FROM_ADDRESS;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_FROM_NAME;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_REPLYTO_ADDRESS;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_REPLYTO_NAME;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_SUBJECT;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_TO_ADDRESS;
import static org.simplejavamail.util.ConfigLoader.Property.DEFAULT_TO_NAME;
import static org.simplejavamail.util.ConfigLoader.getProperty;
import static org.simplejavamail.util.ConfigLoader.hasProperty;

/**
 * Fluent interface Builder for Emails
 *
 * @author Benny Bottema (early work also by Jared Stewart)
 */
@SuppressWarnings("UnusedReturnValue")
public class EmailBuilder {

	/**
	 * Used for replying to emails, when quoting the original email. Matches the beginning of every line.
	 *
	 * @see #asReplyTo(MimeMessage, boolean, String)
	 */
	private static final Pattern LINE_START_PATTERN = compile("(?m)^");

	/**
	 * Default simple quoting markup for email replies.
	 * <p>
	 * <code>{@value DEFAULT_QUOTING_MARKUP}</code>
	 *
	 * @see #asReplyTo(MimeMessage, boolean, String)
	 */
	private static final String DEFAULT_QUOTING_MARKUP = "<blockquote style=\"color: gray; border-left: 1px solid #4f4f4f; padding-left: " +
			"1cm\">%s</blockquote>";

	/**
	 * @see #id(String)
	 */
	private String id;
	
	/**
	 * @see #from(Recipient)
	 * @see #from(String, String)
	 * @see #from(String)
	 */
	private Recipient fromRecipient;
	
	/**
	 * @see #replyTo(String, String)
	 * @see #replyTo(Recipient)
	 */
	private Recipient replyToRecipient;
	
	/**
	 * @see #bounceTo(Recipient)
	 * @see #bounceTo(String, String)
	 */
	private Recipient bounceToRecipient;
	
	/**
	 * @see #text(String)
	 * @see #prependText(String)
	 * @see #appendText(String)
	 */
	private String text;
	
	/**
	 * @see #textHTML(String)
	 * @see #prependTextHTML(String)
	 * @see #appendTextHTML(String)
	 */
	private String textHTML;
	
	/**
	 * @see #subject(String)
	 */
	private String subject;
	
	/**
	 * @see #to(Recipient...)
	 * @see #to(Collection)
	 * @see #to(String...)
	 * @see #to(String)
	 * @see #to(String, String)
	 * @see #to(String, String...)
	 * @see #cc(Recipient...)
	 * @see #cc(Collection)
	 * @see #cc(String...)
	 * @see #cc(String)
	 * @see #cc(String, String)
	 * @see #cc(String, String...)
	 * @see #bcc(Recipient...)
	 * @see #bcc(Collection)
	 * @see #bcc(String...)
	 * @see #bcc(String)
	 * @see #bcc(String, String)
	 * @see #bcc(String, String...)
	 */
	private final Set<Recipient> recipients;
	
	/**
	 * @see #embedImage(String, DataSource)
	 * @see #embedImage(String, byte[], String)
	 * @see #withEmbeddedImages(List)
	 */
	private final List<AttachmentResource> embeddedImages;
	
	/**
	 * @see #addAttachment(String, DataSource)
	 * @see #addAttachment(String, byte[], String)
	 * @see #withAttachments(List)
	 */
	private final List<AttachmentResource> attachments;
	
	/**
	 * @see #addHeader(String, Object)
	 * @see #withHeaders(Map)
	 * @see #asReplyTo(Email)
	 * @see #asReplyTo(Email, String)
	 * @see #asReplyTo(MimeMessage)
	 * @see #asReplyTo(MimeMessage, String)
	 * @see #asReplyTo(MimeMessage, boolean, String)
	 * @see #asReplyToAll(Email)
	 * @see #asReplyToAll(Email, String)
	 * @see #asReplyToAll(MimeMessage)
	 * @see #asReplyToAll(MimeMessage, String)
	 */
	private final Map<String, String> headers;
	
	/**
	 * @see #signWithDomainKey(File, String, String)
	 */
	private File dkimPrivateKeyFile;

	/**
	 * @see #signWithDomainKey(byte[], String, String)
	 * @see #signWithDomainKey(InputStream, String, String)
	 * @see #signWithDomainKey(String, String, String)
	 */
	private InputStream dkimPrivateKeyInputStream;
	
	/**
	 * @see #signWithDomainKey(File, String, String)
	 * @see #signWithDomainKey(byte[], String, String)
	 * @see #signWithDomainKey(InputStream, String, String)
	 * @see #signWithDomainKey(String, String, String)
	 */
	private String signingDomain;
	
	/**
	 * @see #signWithDomainKey(File, String, String)
	 * @see #signWithDomainKey(byte[], String, String)
	 * @see #signWithDomainKey(InputStream, String, String)
	 * @see #signWithDomainKey(String, String, String)
	 */
	private String dkimSelector;
	
	/**
	 * @see #withDispositionNotificationTo()
	 * @see #withDispositionNotificationTo(Recipient)
	 * @see #withDispositionNotificationTo(String, String)
	 * @see #withDispositionNotificationTo(String)
	 */
	private boolean useDispositionNotificationTo;
	
	/**
	 * @see #withDispositionNotificationTo()
	 * @see #withDispositionNotificationTo(Recipient)
	 * @see #withDispositionNotificationTo(String, String)
	 * @see #withDispositionNotificationTo(String)
	 */
	private Recipient dispositionNotificationTo;
	
	/**
	 * @see #withReturnReceiptTo()
	 * @see #withReturnReceiptTo(Recipient)
	 * @see #withReturnReceiptTo(String, String)
	 * @see #withReturnReceiptTo(String)
	 */
	private boolean useReturnReceiptTo;
	
	/**
	 * @see #withReturnReceiptTo()
	 * @see #withReturnReceiptTo(Recipient)
	 * @see #withReturnReceiptTo(String, String)
	 * @see #withReturnReceiptTo(String)
	 */
	private Recipient returnReceiptTo;
	
	/**
	 * @see #asForwardOf(Email)
	 * @see #asForwardOf(MimeMessage)
	 */
	private MimeMessage emailToForward;

	/**
	 * FIXME:
	 * describe what the constructor does related to one of the static builder starters, while referring to the common initialization (either in some super constructor, or because
	 * the static builder starters internally all start with email().
	 */
	// FIXME split up to email(), asReplyTo(), asForwardOf()
	public static EmailBuilder builder() {
		return new EmailBuilder();
	}

	/**
	 * @see EmailBuilder#builder()
	 */
	private EmailBuilder() {
		recipients = new HashSet<>();
		embeddedImages = new ArrayList<>();
		attachments = new ArrayList<>();
		headers = new HashMap<>();
		
		if (hasProperty(DEFAULT_FROM_ADDRESS)) {
			from((String) getProperty(DEFAULT_FROM_NAME), (String) getProperty(DEFAULT_FROM_ADDRESS));
		}
		if (hasProperty(DEFAULT_REPLYTO_ADDRESS)) {
			replyTo((String) getProperty(DEFAULT_REPLYTO_NAME), (String) getProperty(DEFAULT_REPLYTO_ADDRESS));
		}
		if (hasProperty(DEFAULT_BOUNCETO_ADDRESS)) {
			bounceTo((String) getProperty(DEFAULT_BOUNCETO_NAME), (String) getProperty(DEFAULT_BOUNCETO_ADDRESS));
		}
		if (hasProperty(DEFAULT_TO_ADDRESS)) {
			if (hasProperty(DEFAULT_TO_NAME)) {
				to((String) getProperty(DEFAULT_TO_NAME), (String) getProperty(DEFAULT_TO_ADDRESS));
			} else {
				to((String) getProperty(DEFAULT_TO_ADDRESS));
			}
		}
		if (hasProperty(DEFAULT_CC_ADDRESS)) {
			if (hasProperty(DEFAULT_CC_NAME)) {
				cc((String) getProperty(DEFAULT_CC_NAME), (String) getProperty(DEFAULT_CC_ADDRESS));
			} else {
				cc((String) getProperty(DEFAULT_CC_ADDRESS));
			}
		}
		if (hasProperty(DEFAULT_BCC_ADDRESS)) {
			if (hasProperty(DEFAULT_BCC_NAME)) {
				bcc((String) getProperty(DEFAULT_BCC_NAME), (String) getProperty(DEFAULT_BCC_ADDRESS));
			} else {
				bcc((String) getProperty(DEFAULT_BCC_ADDRESS));
			}
		}
		if (hasProperty(DEFAULT_SUBJECT)) {
			subject((String) getProperty(DEFAULT_SUBJECT));
		}
	}

	/**
	 * @return A new immutable {@link Email} instance populated with all the data set on this builder instance.
	 */
	public Email build() {
		return new Email(this);
	}
	
	/**
	 * Sets optional ID, which will be used when sending using the underlying Java Mail framework. Will be generated otherwise.
	 * <p>
	 * Note that id can only ever be filled by end-users for sending an email. This library will never fill this field when converting a MimeMessage.
	 * <p>
	 * The id-format should be conform <a href="https://tools.ietf.org/html/rfc5322#section-3.6.4">rfc5322#section-3.6.4</a>
	 */
	public EmailBuilder id(@Nullable final String id) {
		this.id = id;
		return this;
	}
	
	/**
	 * Delegates to {@link #from(String, String)} with empty name.
	 */
	public EmailBuilder from(@Nonnull final String fromAddress) {
		return from(null, fromAddress);
	}
	
	/**
	 * Delegates to {@link #from(Recipient)} with given name and email address.
	 */
	public EmailBuilder from(@Nullable final String name, @Nonnull final String fromAddress) {
		return from(new Recipient(name, checkNonEmptyArgument(fromAddress, "fromAddress"), null));
	}

	/**
	 * Sets the address of the sender of this email with given {@link Recipient} (ignoring its {@link javax.mail.Message.RecipientType} if provided).
	 * <p>
	 * Can be used in conjunction with one of the {@code replyTo(...)} methods, which is then prioritized by email clients when replying to this email.
	 *
	 * @param recipient Preconfigured recipient which includes optional name and mandatory email address.
	 *
	 * @see #replyTo(Recipient)
	 */
	public EmailBuilder from(@Nonnull final Recipient recipient) {
		checkNonEmptyArgument(recipient, "recipient");
		this.fromRecipient = new Recipient(recipient.getName(), recipient.getAddress(), null);
		return this;
	}
	
	/**
	 * Delegates to {@link #replyTo(Recipient)} with given name and email address.
	 */
	public EmailBuilder replyTo(@Nullable final String name, @Nonnull final String replyToAddress) {
		return replyTo(new Recipient(name, checkNonEmptyArgument(replyToAddress, "replyToAddress"), null));
	}

	/**
	 * Sets the <em>replyTo</em> address of this email with given {@link Recipient} (ignoring its {@link javax.mail.Message.RecipientType} if provided).
	 * <p>
	 * If provided, email clients should prioritize the <em>replyTo</em> recipient over the <em>from</em> recipient when replying to this email.
	 *
	 * @param recipient Preconfigured recipient which includes optional name and mandatory email address.
	 */
	public EmailBuilder replyTo(@Nonnull final Recipient recipient) {
		checkNonEmptyArgument(recipient, "replyToRecipient");
		this.replyToRecipient = new Recipient(recipient.getName(), recipient.getAddress(), null);
		return this;
	}

	/**
	 * Delegates to {@link #bounceTo(Recipient)} with given name and email address.
	 */
	public EmailBuilder bounceTo(@Nullable final String name, @Nonnull final String bounceToAddress) {
		return bounceTo(new Recipient(name, checkNonEmptyArgument(bounceToAddress, "bounceToAddress"), null));
	}

	/**
	 * Sets the <em>bounceTo</em> address of this email with given {@link Recipient} (ignoring its {@link javax.mail.Message.RecipientType} if provided).
	 * <p>
	 * If provided, SMTP server should return bounced emails to this address. This is also known as the {@code Return-Path} (or <em>Envelope FROM</em>).
	 *
	 * @param recipient Preconfigured recipient which includes optional name and mandatory email address.
	 */
	public EmailBuilder bounceTo(@Nonnull final Recipient recipient) {
		checkNonEmptyArgument(recipient, "bounceToRecipient");
		this.bounceToRecipient = new Recipient(recipient.getName(), recipient.getAddress(), null);
		return this;
	}
	
	/**
	 * Sets the {@link #subject} of this email.
	 */
	public EmailBuilder subject(@Nonnull final String subject) {
		this.subject = checkNonEmptyArgument(subject, "subject");
		return this;
	}
	
	/**
	 * Sets the optional email message body in plain text.
	 * <p>
	 * Both text and HTML can be provided, which will  be offered to the email client as alternative content. Email clients that support it, will favor HTML
	 * over plain text and ignore the text body completely.
	 */
	public EmailBuilder text(@Nullable final String text) {
		this.text = text;
		return this;
	}

	/**
	 * Prepends text to the current plain text body (or starts it if plain text body is missing).
	 *
	 * @see #text(String)
	 */
	public EmailBuilder prependText(@Nonnull final String text) {
		this.text = text + defaultTo(this.text, "");
		return this;
	}

	/**
	 * Appends text to the current plain text body (or starts it if plain text body is missing).
	 *
	 * @see #text(String)
	 */
	public EmailBuilder appendText(@Nonnull final String text) {
		this.text = defaultTo(this.text, "") + text;
		return this;
	}

	/**
	 * Sets the optional email message body in HTML text.
	 * <p>
	 * Both text and HTML can be provided, which will  be offered to the email client as alternative content. Email clients that support it, will favor HTML
	 * over plain text and ignore the text body completely.
	 */
	public EmailBuilder textHTML(@Nullable final String textHTML) {
		this.textHTML = textHTML;
		return this;
	}

	/**
	 * Prepends HTML text to the current HTML text body (or starts it if HTML text body is missing).
	 *
	 * @see #textHTML(String)
	 */
	public EmailBuilder prependTextHTML(@Nonnull final String textHTML) {
		this.textHTML = textHTML + defaultTo(this.textHTML, "");
		return this;
	}

	/**
	 * Appends HTML text to the current HTML text body (or starts it if HTML text body is missing).
	 *
	 * @see #textHTML(String)
	 */
	public EmailBuilder appendTextHTML(@Nonnull final String textHTML) {
		this.textHTML = defaultTo(this.textHTML, "") + textHTML;
		return this;
	}

	FIXME: continue replacing JavaDoc with proper documentation, from here down...
	/**
	 * Adds new {@link Recipient} instances to the list on account of name, address with recipient type {@link Message.RecipientType#TO}.
	 *
	 * @param recipientsToAdd The recipients whose name and address to use
	 * @see #recipients
	 * @see Recipient
	 */
	public EmailBuilder to(@Nonnull final Recipient... recipientsToAdd) {
		return to(asList(recipientsToAdd));
	}
	
	/**
	 * Adds new {@link Recipient} instances to the list on account of name, address with recipient type {@link Message.RecipientType#TO}.
	 *
	 * @param recipientsToAdd The recipients whose name and address to use
	 * @see #recipients
	 * @see Recipient
	 */
	public EmailBuilder to(@Nonnull final Collection<Recipient> recipientsToAdd) {
		for (final Recipient recipient : checkNonEmptyArgument(recipientsToAdd, "recipientsToAdd")) {
			recipients.add(new Recipient(recipient.getName(), recipient.getAddress(), Message.RecipientType.TO));
		}
		return this;
	}
	
	/**
	 * Delegates to {@link #to(String, String)} while omitting the name used for the recipient(s).
	 */
	public EmailBuilder to(@Nonnull final String emailAddressList) {
		return to(null, emailAddressList);
	}
	
	/**
	 * Adds a new {@link Recipient} instances to the list on account of given name, address with recipient type {@link Message.RecipientType#TO}.
	 * List can be comma ',' or semicolon ';' separated.
	 *
	 * @param name             The name of the recipient(s).
	 * @param emailAddressList The emailaddresses of the recipients (will be singular in most use cases).
	 * @see #recipients
	 * @see Recipient
	 */
	public EmailBuilder to(@Nullable final String name, @Nonnull final String emailAddressList) {
		checkNonEmptyArgument(emailAddressList, "emailAddressList");
		return addCommaOrSemicolonSeparatedEmailAddresses(name, emailAddressList, Message.RecipientType.TO);
	}
	
	@Nonnull
	private EmailBuilder addCommaOrSemicolonSeparatedEmailAddresses(@Nullable final String name, @Nonnull final String emailAddressList, @Nonnull final Message.RecipientType type) {
		checkNonEmptyArgument(type, "type");
		for (final String emailAddress : extractEmailAddresses(checkNonEmptyArgument(emailAddressList, "emailAddressList"))) {
			recipients.add(Email.interpretRecipientData(name, emailAddress, type));
		}
		return this;
	}

	/**
	 * Delegates to {@link #to(String, String...)} with empty name.
	 */
	public EmailBuilder to(@Nonnull final String... emailAddresses) {
		return to(null, emailAddresses);
	}

	/**
	 * Adds new {@link Recipient} instances to the list on account of given name, address with recipient type {@link Message.RecipientType#TO}.
	 *
	 * @param name           The name to use for each given address.
	 * @param emailAddresses The recipients whose address to use for both name and address
	 * @see #recipients
	 * @see Recipient
	 */
	public EmailBuilder to(@Nullable final String name, @Nonnull final String... emailAddresses) {
		for (final String emailAddress : checkNonEmptyArgument(emailAddresses, "emailAddresses")) {
			recipients.add(new Recipient(name, emailAddress, Message.RecipientType.TO));
		}
		return this;
	}

	/**
	 * Delegates to {@link #cc(String, String...)} with empty name.
	 */
	@SuppressWarnings("QuestionableName")
	public EmailBuilder cc(@Nonnull final String... emailAddresses) {
		return cc(null, emailAddresses);
	}

	/**
	 * Adds new {@link Recipient} instances to the list on account of given name, address with recipient type {@link Message.RecipientType#CC}.
	 *
	 * @param emailAddresses The recipients whose address to use for both name and address
	 * @see #recipients
	 * @see Recipient
	 */
	@SuppressWarnings("QuestionableName")
	public EmailBuilder cc(@Nullable final String name, @Nonnull final String... emailAddresses) {
		for (final String emailAddress : checkNonEmptyArgument(emailAddresses, "emailAddresses")) {
			recipients.add(new Recipient(name, emailAddress, Message.RecipientType.CC));
		}
		return this;
	}
	
	
	/**
	 * Delegates to {@link #cc(String, String)} while omitting the name for the CC recipient(s).
	 */
	@SuppressWarnings("QuestionableName")
	public EmailBuilder cc(@Nonnull final String emailAddressList) {
		return cc(null, emailAddressList);
	}
	
	/**
	 * Adds a new {@link Recipient} instances to the list on account of empty name, address with recipient type {@link Message.RecipientType#CC}. List can be
	 * comma ',' or semicolon ';' separated.
	 *
	 * @param name             The name of the recipient(s).
	 * @param emailAddressList The recipients whose address to use for both name and address
	 * @see #recipients
	 * @see Recipient
	 */
	@SuppressWarnings("QuestionableName")
	public EmailBuilder cc(@Nullable final String name, @Nonnull final String emailAddressList) {
		checkNonEmptyArgument(emailAddressList, "emailAddressList");
		return addCommaOrSemicolonSeparatedEmailAddresses(name, emailAddressList, Message.RecipientType.CC);
	}
	
	/**
	 * Adds new {@link Recipient} instances to the list on account of name, address with recipient type {@link Message.RecipientType#CC}.
	 *
	 * @param recipientsToAdd The recipients whose name and address to use
	 * @see #recipients
	 * @see Recipient
	 */
	@SuppressWarnings("QuestionableName")
	public EmailBuilder cc(@Nonnull final Recipient... recipientsToAdd) {
		for (final Recipient recipient : checkNonEmptyArgument(recipientsToAdd, "recipientsToAdd")) {
			recipients.add(new Recipient(recipient.getName(), recipient.getAddress(), Message.RecipientType.CC));
		}
		return this;
	}

	/**
	 * Adds new {@link Recipient} instances to the list on account of name, address with recipient type {@link Message.RecipientType#CC}.
	 *
	 * @param recipientsToAdd The recipients whose name and address to use
	 * @see #recipients
	 * @see Recipient
	 */
	@SuppressWarnings("QuestionableName")
	public EmailBuilder cc(@Nonnull final Collection<Recipient> recipientsToAdd) {
		for (final Recipient recipient : checkNonEmptyArgument(recipientsToAdd, "recipientsToAdd")) {
			recipients.add(new Recipient(recipient.getName(), recipient.getAddress(), Message.RecipientType.CC));
		}
		return this;
	}

	/**
	 * Delegates to {@link #bcc(String, String...)} with empty name.
	 */
	public EmailBuilder bcc(@Nonnull final String... emailAddresses) {
		return bcc(null, emailAddresses);
	}

	/**
	 * Adds new {@link Recipient} instances to the list on account of given name, address with recipient type {@link Message.RecipientType#BCC}.
	 *
	 * @param emailAddresses The recipients whose address to use for both name and address
	 * @see #recipients
	 * @see Recipient
	 */
	public EmailBuilder bcc(@Nullable final String name, @Nonnull final String... emailAddresses) {
		for (final String emailAddress : checkNonEmptyArgument(emailAddresses, "emailAddresses")) {
			recipients.add(new Recipient(name, emailAddress, Message.RecipientType.BCC));
		}
		return this;
	}
	
	/**
	 * Delegates to {@link #bcc(String, String)} while omitting the name for the BCC recipient(s).
	 */
	public EmailBuilder bcc(@Nonnull final String emailAddressList) {
		return bcc(null, emailAddressList);
	}
	
	/**
	 * Adds a new {@link Recipient} instances to the list on account of empty name, address with recipient type {@link Message.RecipientType#BCC}. List can be
	 * comma ',' or semicolon ';' separated.
	 *
	 * @param name             The name of the recipient(s).
	 * @param emailAddressList The recipients whose address to use for both name and address
	 * @see #recipients
	 * @see Recipient
	 */
	public EmailBuilder bcc(@Nullable final String name, @Nonnull final String emailAddressList) {
		checkNonEmptyArgument(emailAddressList, "emailAddressList");
		return addCommaOrSemicolonSeparatedEmailAddresses(name, emailAddressList, Message.RecipientType.BCC);
	}
	
	/**
	 * Adds new {@link Recipient} instances to the list on account of name, address with recipient type {@link Message.RecipientType#BCC}.
	 *
	 * @param recipientsToAdd The recipients whose name and address to use
	 * @see #recipients
	 * @see Recipient
	 */
	public EmailBuilder bcc(@Nonnull final Recipient... recipientsToAdd) {
		for (final Recipient recipient : checkNonEmptyArgument(recipientsToAdd, "recipientsToAdd")) {
			recipients.add(new Recipient(recipient.getName(), recipient.getAddress(), Message.RecipientType.BCC));
		}
		return this;
	}

	/**
	 * Adds new {@link Recipient} instances to the list on account of name, address with recipient type {@link Message.RecipientType#BCC}.
	 *
	 * @param recipientsToAdd The recipients whose name and address to use
	 * @see #recipients
	 * @see Recipient
	 */
	@SuppressWarnings("QuestionableName")
	public EmailBuilder bcc(@Nonnull final Collection<Recipient> recipientsToAdd) {
		for (final Recipient recipient : checkNonEmptyArgument(recipientsToAdd, "recipientsToAdd")) {
			recipients.add(new Recipient(recipient.getName(), recipient.getAddress(), Message.RecipientType.BCC));
		}
		return this;
	}
	
	/**
	 * Adds an embedded image (attachment type) to the email message and generates the necessary {@link DataSource} with the given byte data. Then
	 * delegates to {@link Email#addEmbeddedImage(String, DataSource)}. At this point the datasource is actually a {@link ByteArrayDataSource}.
	 *
	 * @param name     The name of the image as being referred to from the message content body (eg. 'signature').
	 * @param data     The byte data of the image to be embedded.
	 * @param mimetype The content type of the given data (eg. "image/gif" or "image/jpeg").
	 * @see ByteArrayDataSource
	 * @see Email#addEmbeddedImage(String, DataSource)
	 */
	public EmailBuilder embedImage(@Nonnull final String name, @Nonnull final byte[] data, @Nonnull final String mimetype) {
		checkNonEmptyArgument(name, "name");
		checkNonEmptyArgument(data, "data");
		checkNonEmptyArgument(mimetype, "mimetype");
		
		final ByteArrayDataSource dataSource = new ByteArrayDataSource(data, mimetype);
		dataSource.setName(name);
		return embedImage(name, dataSource);
	}
	
	/**
	 * Delegates to {@link #embedImage(String, DataSource)} for each embedded image.
	 */
	private EmailBuilder withEmbeddedImages(@Nonnull final List<AttachmentResource> embeddedImages) {
		for (final AttachmentResource embeddedImage : embeddedImages) {
			embedImage(embeddedImage.getName(), embeddedImage.getDataSource());
		}
		return this;
	}
	
	/**
	 * Overloaded method which sets an embedded image on account of name and {@link DataSource}.
	 *
	 * @param name      The name of the image as being referred to from the message content body (eg. 'embeddedimage'). If not provided, the name of the given
	 *                  data source is used instead.
	 * @param imagedata The image data.
	 */
	@SuppressWarnings("WeakerAccess")
	public EmailBuilder embedImage(@Nullable final String name, @Nonnull final DataSource imagedata) {
		checkNonEmptyArgument(imagedata, "imagedata");
		if (valueNullOrEmpty(name) && valueNullOrEmpty(imagedata.getName())) {
			throw new EmailException(EmailException.NAME_MISSING_FOR_EMBEDDED_IMAGE);
		}
		embeddedImages.add(new AttachmentResource(name, imagedata));
		return this;
	}
	
	@SuppressWarnings("WeakerAccess")
	public EmailBuilder withHeaders(@Nonnull final Map<String, String> headers) {
		this.headers.putAll(headers);
		return this;
	}
	
	/**
	 * Adds a header to the {@link #headers} list. The value is stored as a <code>String</code>. example: <code>email.addHeader("X-Priority",
	 * 2)</code>
	 *
	 * @param name  The name of the header.
	 * @param value The value of the header, which will be stored using {@link String#valueOf(Object)}.
	 */
	public EmailBuilder addHeader(@Nonnull final String name, @Nonnull final Object value) {
		checkNonEmptyArgument(name, "name");
		checkNonEmptyArgument(value, "value");
		headers.put(name, String.valueOf(value));
		return this;
	}
	
	/**
	 * Adds an attachment to the email message and generates the necessary {@link DataSource} with the given byte data. Then delegates to {@link
	 * #addAttachment(String, DataSource)}. At this point the datasource is actually a {@link ByteArrayDataSource}.
	 *
	 * @param name     The name of the extension (eg. filename including extension).
	 * @param data     The byte data of the attachment.
	 * @param mimetype The content type of the given data (eg. "plain/text", "image/gif" or "application/pdf").
	 * @see ByteArrayDataSource
	 * @see #addAttachment(String, DataSource)
	 */
	public EmailBuilder addAttachment(@Nullable final String name, @Nonnull final byte[] data, @Nonnull final String mimetype) {
		checkNonEmptyArgument(data, "data");
		checkNonEmptyArgument(mimetype, "mimetype");
		final ByteArrayDataSource dataSource = new ByteArrayDataSource(data, mimetype);
		dataSource.setName(MiscUtil.encodeText(name));
		addAttachment(MiscUtil.encodeText(name), dataSource);
		return this;
	}
	
	/**
	 * Overloaded method which sets an attachment on account of name and {@link DataSource}.
	 *
	 * @param name     The name of the attachment (eg. 'filename.ext').
	 * @param filedata The attachment data.
	 */
	public EmailBuilder addAttachment(@Nullable final String name, @Nonnull final DataSource filedata) {
		checkNonEmptyArgument(filedata, "filedata");
		attachments.add(new AttachmentResource(MiscUtil.encodeText(name), filedata));
		return this;
	}

	/**
	 * Delegates to {@link #addAttachment(String, DataSource)} for each attachment.
	 */
	public EmailBuilder withAttachments(@Nonnull final List<AttachmentResource> attachments) {
		for (final AttachmentResource attachment : attachments) {
			addAttachment(attachment.getName(), attachment.getDataSource());
		}
		return this;
	}
	
	/**
	 * Sets all info needed for DKIM, using a byte array for private key data.
	 */
	public EmailBuilder signWithDomainKey(@Nonnull final byte[] dkimPrivateKey, @Nonnull final String signingDomain, @Nonnull final String dkimSelector) {
		this.dkimPrivateKeyInputStream = new ByteArrayInputStream(checkNonEmptyArgument(dkimPrivateKey, "dkimPrivateKey"));
		this.signingDomain = checkNonEmptyArgument(signingDomain, "signingDomain");
		this.dkimSelector = checkNonEmptyArgument(dkimSelector, "dkimSelector");
		return this;
	}
	
	/**
	 * Sets all info needed for DKIM, using a byte array for private key data.
	 */
	public EmailBuilder signWithDomainKey(@Nonnull final String dkimPrivateKey, @Nonnull final String signingDomain, @Nonnull final String dkimSelector) {
		checkNonEmptyArgument(dkimPrivateKey, "dkimPrivateKey");
		this.dkimPrivateKeyInputStream = new ByteArrayInputStream(dkimPrivateKey.getBytes(UTF_8));
		this.signingDomain = checkNonEmptyArgument(signingDomain, "signingDomain");
		this.dkimSelector = checkNonEmptyArgument(dkimSelector, "dkimSelector");
		return this;
	}
	
	/**
	 * Sets all info needed for DKIM, using a file reference for private key data.
	 */
	public EmailBuilder signWithDomainKey(@Nonnull final File dkimPrivateKeyFile, @Nonnull final String signingDomain, @Nonnull final String dkimSelector) {
		this.dkimPrivateKeyFile = checkNonEmptyArgument(dkimPrivateKeyFile, "dkimPrivateKeyFile");
		this.signingDomain = checkNonEmptyArgument(signingDomain, "signingDomain");
		this.dkimSelector = checkNonEmptyArgument(dkimSelector, "dkimSelector");
		return this;
	}
	
	/**
	 * Sets all info needed for DKIM, using an input stream for private key data.
	 */
	public EmailBuilder signWithDomainKey(@Nonnull final InputStream dkimPrivateKeyInputStream, @Nonnull final String signingDomain,
										  @Nonnull final String dkimSelector) {
		this.dkimPrivateKeyInputStream = checkNonEmptyArgument(dkimPrivateKeyInputStream, "dkimPrivateKeyInputStream");
		this.signingDomain = checkNonEmptyArgument(signingDomain, "signingDomain");
		this.dkimSelector = checkNonEmptyArgument(dkimSelector, "dkimSelector");
		return this;
	}
	
	/**
	 * Indicates that we want to use the NPM flag {@link #dispositionNotificationTo}. The actual address will default to the {@link #replyToRecipient}
	 * first if set or else {@link #fromRecipient}.
	 */
	public EmailBuilder withDispositionNotificationTo() {
		this.useDispositionNotificationTo = true;
		this.dispositionNotificationTo = null;
		return this;
	}
	
	/**
	 * Indicates that we want to use the NPM flag {@link #dispositionNotificationTo} with the given mandatory address.
	 */
	public EmailBuilder withDispositionNotificationTo(@Nonnull final String address) {
		this.useDispositionNotificationTo = true;
		this.dispositionNotificationTo = new Recipient(null, checkNonEmptyArgument(address, "dispositionNotificationToAddress"), null);
		return this;
	}
	
	/**
	 * Indicates that we want to use the NPM flag {@link #dispositionNotificationTo} with the given optional name and mandatory address.
	 */
	public EmailBuilder withDispositionNotificationTo(@Nullable final String name, @Nonnull final String address) {
		this.useDispositionNotificationTo = true;
		this.dispositionNotificationTo = new Recipient(name, checkNonEmptyArgument(address, "dispositionNotificationToAddress"), null);
		return this;
	}
	
	/**
	 * Indicates that we want to use the NPM flag {@link #dispositionNotificationTo} with the given preconfigred {@link Recipient}.
	 */
	public EmailBuilder withDispositionNotificationTo(@Nonnull final Recipient recipient) {
		this.useDispositionNotificationTo = true;
		this.dispositionNotificationTo = new Recipient(recipient.getName(), checkNonEmptyArgument(recipient.getAddress(), "dispositionNotificationToAddress"), null);
		return this;
	}
	
	/**
	 * Indicates that we want to use the flag {@link #returnReceiptTo}. The actual address will default to the {@link #replyToRecipient}
	 * first if set or else {@link #fromRecipient}.
	 */
	public EmailBuilder withReturnReceiptTo() {
		this.useReturnReceiptTo = true;
		this.returnReceiptTo = null;
		return this;
	}
	
	/**
	 * Indicates that we want to use the NPM flag {@link #returnReceiptTo} with the given mandatory address.
	 */
	public EmailBuilder withReturnReceiptTo(@Nonnull final String address) {
		this.useReturnReceiptTo = true;
		this.returnReceiptTo = new Recipient(null, checkNonEmptyArgument(address, "returnReceiptToAddress"), null);
		return this;
	}
	
	/**
	 * Indicates that we want to use the NPM flag {@link #returnReceiptTo} with the given optional name and mandatory address.
	 */
	public EmailBuilder withReturnReceiptTo(@Nullable final String name, @Nonnull final String address) {
		this.useReturnReceiptTo = true;
		this.returnReceiptTo = new Recipient(name, checkNonEmptyArgument(address, "returnReceiptToAddress"), null);
		return this;
	}
	
	/**
	 * Indicates that we want to use the NPM flag {@link #returnReceiptTo} with the preconfigured {@link Recipient}.
	 */
	public EmailBuilder withReturnReceiptTo(@Nonnull final Recipient recipient) {
		this.useReturnReceiptTo = true;
		this.returnReceiptTo = new Recipient(recipient.getName(), checkNonEmptyArgument(recipient.getAddress(), "returnReceiptToAddress"), null);
		return this;
	}
	
	/**
	 * Delegates to {@link #asReplyTo(MimeMessage, boolean, String)} with replyToAll set to <code>false</code> and a default HTML quoting
	 * template.
	 */
	public EmailBuilder asReplyTo(@Nonnull final Email email) {
		return asReplyTo(EmailConverter.emailToMimeMessage(email), false, DEFAULT_QUOTING_MARKUP);
	}
	
	/**
	 * Delegates to {@link #asReplyTo(MimeMessage, boolean, String)} with replyToAll set to <code>true</code> and a default HTML quoting
	 * template.
	 */
	public EmailBuilder asReplyToAll(@Nonnull final Email email) {
		return asReplyTo(EmailConverter.emailToMimeMessage(email), true, DEFAULT_QUOTING_MARKUP);
	}
	
	/**
	 * Delegates to {@link #asReplyTo(MimeMessage, boolean, String)} with replyToAll set to <code>true</code>.
	 *
	 * @see EmailBuilder#DEFAULT_QUOTING_MARKUP
	 */
	public EmailBuilder asReplyToAll(@Nonnull final Email email, @Nonnull final String customQuotingTemplate) {
		return asReplyTo(EmailConverter.emailToMimeMessage(email), true, customQuotingTemplate);
	}
	
	/**
	 * Delegates to {@link #asReplyTo(MimeMessage, boolean, String)} with replyToAll set to <code>false</code>.
	 */
	public EmailBuilder asReplyTo(@Nonnull final Email email, @Nonnull final String customQuotingTemplate) {
		return asReplyTo(EmailConverter.emailToMimeMessage(email), false, customQuotingTemplate);
	}
	
	/**
	 * Delegates to {@link #asReplyTo(MimeMessage, boolean, String)} with replyToAll set to <code>false</code> and a default HTML quoting
	 * template.
	 */
	public EmailBuilder asReplyTo(@Nonnull final MimeMessage email) {
		return asReplyTo(email, false, DEFAULT_QUOTING_MARKUP);
	}
	
	/**
	 * Delegates to {@link #asReplyTo(MimeMessage, boolean, String)} with replyToAll set to <code>true</code>.
	 *
	 * @see EmailBuilder#DEFAULT_QUOTING_MARKUP
	 */
	public EmailBuilder asReplyToAll(@Nonnull final MimeMessage email, @Nonnull final String customQuotingTemplate) {
		return asReplyTo(email, true, customQuotingTemplate);
	}
	
	/**
	 * Delegates to {@link #asReplyTo(MimeMessage, boolean, String)} with replyToAll set to <code>false</code>.
	 */
	public EmailBuilder asReplyTo(@Nonnull final MimeMessage email, @Nonnull final String customQuotingTemplate) {
		return asReplyTo(email, false, customQuotingTemplate);
	}
	
	/**
	 * Delegates to {@link #asReplyTo(MimeMessage, boolean, String)} with replyToAll set to <code>true</code> and a default HTML quoting
	 * template.
	 *
	 * @see EmailBuilder#DEFAULT_QUOTING_MARKUP
	 */
	public EmailBuilder asReplyToAll(@Nonnull final MimeMessage email) {
		return asReplyTo(email, true, DEFAULT_QUOTING_MARKUP);
	}

	/**
	 * Primes the email with all subject, headers, originally embedded images and recipients needed for a valid RFC reply.
	 * <p>
	 * <strong>Note:</strong> replaces subject with "Re: &lt;original subject&gt;" (but never nested).<br>
	 * <p>
	 * <strong>Note:</strong> Make sure you set the content before using this API or else the quoted content is lost. Replaces body (text is replaced
	 * with "> text" and HTML is replaced with the provided or default quoting markup.
	 *
	 * @param htmlTemplate A valid HTML that contains the string {@code "%s"}. Be advised that HTML is very limited in emails.
	 * @see <a href="https://javaee.github.io/javamail/FAQ#reply">Official JavaMail FAQ on replying</a>
	 * @see javax.mail.internet.MimeMessage#reply(boolean)
	 */
	public EmailBuilder asReplyTo(@Nonnull final MimeMessage emailMessage, final boolean repyToAll, @Nonnull final String htmlTemplate) {
		final MimeMessage replyMessage;
		try {
			replyMessage = (MimeMessage) emailMessage.reply(repyToAll);
			replyMessage.setText("ignore");
			replyMessage.setFrom("ignore@ignore.ignore");
		} catch (final MessagingException e) {
			throw new EmailException("was unable to parse mimemessage to produce a reply for", e);
		}
		
		final Email repliedTo = EmailConverter.mimeMessageToEmail(emailMessage);
		final Email generatedReply = EmailConverter.mimeMessageToEmail(replyMessage);

		return this
				.subject(generatedReply.getSubject())
				.to(generatedReply.getRecipients())
				.text(valueNullOrEmpty(repliedTo.getText()) ? text : text + LINE_START_PATTERN.matcher(repliedTo.getText()).replaceAll("> "))
				.textHTML(valueNullOrEmpty(repliedTo.getTextHTML()) ? textHTML : textHTML + format(htmlTemplate, repliedTo.getTextHTML()))
				.withHeaders(generatedReply.getHeaders())
				.withEmbeddedImages(repliedTo.getEmbeddedImages());
	}
	
	/**
	 * Delegates to {@link #asForwardOf(MimeMessage)}.
	 *
	 * @see EmailConverter#emailToMimeMessage(Email)
	 */
	public EmailBuilder asForwardOf(@Nonnull final Email email) {
		return asForwardOf(EmailConverter.emailToMimeMessage(email));
	}
	
	/**
	 * Primes the email to build with proper subject and inline forwarded email needed for a valid RFC forward.
	 * <p>
	 * <strong>Note</strong>: replaces subject with "Fwd: &lt;original subject&gt;" (nesting enabled).
	 * <p>
	 * <strong>Note</strong>: {@code Content-Disposition} will be left empty so the receiving email client can decide how to handle display (most will show
	 * inline, some will show as attachment instead).
	 *
	 * @see <a href="https://javaee.github.io/javamail/FAQ#forward">Official JavaMail FAQ on forwarding</a>
	 * @see <a href="https://blogs.technet.microsoft.com/exchange/2011/04/21/mixed-ing-it-up-multipartmixed-messages-and-you/">More reading
	 * material</a>
	 */
	public EmailBuilder asForwardOf(@Nonnull final MimeMessage emailMessage) {
		this.emailToForward = emailMessage;
		return subject("Fwd: " + MimeMessageParser.parseSubject(emailMessage));
	}
	
	/*
		SETTERS / GETTERS
	 */
	
	public String getId() {
		return id;
	}
	
	public Recipient getFromRecipient() {
		return fromRecipient;
	}
	
	public Recipient getReplyToRecipient() {
		return replyToRecipient;
	}
	
	public Recipient getBounceToRecipient() {
		return bounceToRecipient;
	}
	
	public String getText() {
		return text;
	}
	
	public String getTextHTML() {
		return textHTML;
	}
	
	public String getSubject() {
		return subject;
	}
	
	public List<Recipient> getRecipients() {
		return new ArrayList<>(recipients);
	}
	
	public List<AttachmentResource> getEmbeddedImages() {
		return new ArrayList<>(embeddedImages);
	}
	
	public List<AttachmentResource> getAttachments() {
		return new ArrayList<>(attachments);
	}
	
	public Map<String, String> getHeaders() {
		return new HashMap<>(headers);
	}
	
	public File getDkimPrivateKeyFile() {
		return dkimPrivateKeyFile;
	}
	
	public InputStream getDkimPrivateKeyInputStream() {
		return dkimPrivateKeyInputStream;
	}
	
	public String getSigningDomain() {
		return signingDomain;
	}
	
	public String getDkimSelector() {
		return dkimSelector;
	}
	
	public boolean isUseDispositionNotificationTo() {
		return useDispositionNotificationTo;
	}
	
	public Recipient getDispositionNotificationTo() {
		return dispositionNotificationTo;
	}
	
	public boolean isUseReturnReceiptTo() {
		return useReturnReceiptTo;
	}
	
	public Recipient getReturnReceiptTo() {
		return returnReceiptTo;
	}
	
	public MimeMessage getEmailToForward() {
		return emailToForward;
	}
}