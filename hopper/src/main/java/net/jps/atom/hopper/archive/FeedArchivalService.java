package net.jps.atom.hopper.archive;

import net.jps.atom.hopper.adapter.FeedSource;
import net.jps.atom.hopper.adapter.archive.FeedArchiver;

/**
 *
 * 
 */
public interface FeedArchivalService {

    void startService();

    void stopService();

    void registerArchiveTask(FeedSource feedSource, FeedArchiver archiver);
}