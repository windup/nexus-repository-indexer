package org.jboss.windup.rules.apps.java.archives;

import java.util.logging.Logger;

import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.observers.AbstractTransferListener;

/**
 * This is used to report progress of potentially large file downloads.
 * 
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
class LoggingTransferListener extends AbstractTransferListener
{
    private int percent = 0;
    private int progress = 0;
    private Logger log;

    public LoggingTransferListener(Logger log)
    {
        this.log = log;
    }

    public void transferStarted(TransferEvent transferEvent)
    {
        log.info(transferEvent.getResource().getName() + "download beginning.");
    }

    @Override
    public void transferProgress(TransferEvent transferEvent, byte[] buffer, int length)
    {
        progress += length;
        long contentLength = transferEvent.getResource().getContentLength();
        long percentComplete = (long) (progress * 100.0 / contentLength);

        if (((int) percentComplete / 10) == percent)
        {
            log.info(transferEvent.getResource().getName() + " - " + percentComplete + "% (" + progress
                        + "/" + contentLength + ")");
            percent++;
        }
    }

    public void transferCompleted(TransferEvent transferEvent)
    {
        log.info(transferEvent.getResource().getName() + " downloaded.");
        log.info("Indexing and sorting metadata may take some time...");
    }
}