/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.client.file.cache.store;

import alluxio.client.file.cache.PageId;
import alluxio.exception.PageNotFoundException;
import alluxio.client.file.cache.PageStore;

import com.google.common.base.Preconditions;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * The {@link LocalPageStore} is an implementation of {@link PageStore} which
 * stores all pages in a directory somewhere on the local disk.
 */
@NotThreadSafe
public class LocalPageStore implements PageStore {
  private static final Logger LOG = LoggerFactory.getLogger(LocalPageStore.class);

  private final String mRoot;
  private final AtomicInteger mSize = new AtomicInteger(0);

  /**
   * Creates a new instance of {@link LocalPageStore}.
   *
   * @param options options for the local page store
   */
  public LocalPageStore(LocalPageStoreOptions options) {
    mRoot = options.getRootDir();
  }

  @Override
  public void put(PageId pageId, byte[] page) throws IOException {
    Path p = getFilePath(pageId);
    if (!Files.exists(p)) {
      Path parent = Preconditions.checkNotNull(p.getParent(),
          "parent of cache file should not be null");
      Files.createDirectories(parent);
      Files.createFile(p);
    }
    try (FileOutputStream fos = new FileOutputStream(p.toFile(), false)) {
      fos.write(page);
    }
    mSize.incrementAndGet();
  }

  @Override
  public ReadableByteChannel get(PageId pageId) throws IOException, PageNotFoundException {
    Path p = getFilePath(pageId);
    if (!Files.exists(p)) {
      throw new PageNotFoundException(p.toString());
    }
    FileInputStream fis = new FileInputStream(p.toFile());
    return fis.getChannel();
  }

  @Override
  public void delete(PageId pageId) throws IOException, PageNotFoundException {
    Path p = getFilePath(pageId);
    if (!Files.exists(p)) {
      throw new PageNotFoundException(p.toString());
    }
    Files.delete(p);
    mSize.decrementAndGet();
    Path parent = Preconditions.checkNotNull(p.getParent(),
        "parent of cache file should not be null");
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
      if (!stream.iterator().hasNext()) {
        Files.delete(parent);
      }
    }
  }

  private Path getFilePath(PageId pageId) {
    return Paths.get(mRoot, Long.toString(pageId.getFileId()),
        Long.toString(pageId.getPageIndex()));
  }

  @Override
  public void close() {
    try {
      FileUtils.deleteDirectory(new File(mRoot));
    } catch (IOException e) {
      LOG.warn("Failed to clean up local page store directory", e);
    }
  }

  @Override
  public int size() {
    return mSize.get();
  }
}