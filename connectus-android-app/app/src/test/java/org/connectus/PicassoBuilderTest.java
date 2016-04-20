package org.connectus;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PicassoBuilderTest {

    @Test
    public void matchAttachmentRequest() {
        assertThat(PicassoBuilder.matchAttachmentRequest("https://www.googleapis.com/gmail/v1/users/user1@gmail.com/messages/messageId/attachments/id?accessToken=fakeAccessToken&mimeType=image/png")).isTrue();
        assertThat(PicassoBuilder.matchAttachmentRequest("https://www.googleapis.com/gmail/v1/users/user2@gmail.com/messages/messageId?accessToken=fakeAccessToken&mimeType=image/png")).isFalse();
        assertThat(PicassoBuilder.matchAttachmentRequest("https://www.googleapis.com/gmail/v1/users/user3@gmail.com/labels/id?accessToken=fakeAccessToken&mimeType=image/png")).isFalse();
    }
}

