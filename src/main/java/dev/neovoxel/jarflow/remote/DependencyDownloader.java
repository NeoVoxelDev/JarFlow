package dev.neovoxel.jarflow.remote;

import dev.neovoxel.jarflow.dependency.Dependency;
import dev.neovoxel.jarflow.repository.Repository;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DependencyDownloader {

    private static final Logger logger = LoggerFactory.getLogger("JarFlow Downloader");

    public static void download(String url, Dependency dependency, File libDirPath, int threadNum) throws IOException, InterruptedException {
        libDirPath.mkdirs();
        Path path = libDirPath.toPath()
                .resolve(dependency.getGroupId())
                .resolve(dependency.getArtifactId())
                .resolve(dependency.getVersion());
        path.toFile().mkdirs();
        downloadWithMultipleThreads(url, path.resolve(dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar").toString(), threadNum);
    }

    private static void downloadWithMultipleThreads(String fileUrl, String savePath, int threadNum)
            throws InterruptedException, IOException {
        logger.info("Ready to download {} with {} threads to {}", fileUrl, threadNum, savePath);

        // 创建保存目录
        File saveFile = new File(savePath);
        File parentDir = saveFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.connect();

            // 检查服务器是否支持断点续传
            String acceptRanges = connection.getHeaderField("Accept-Ranges");
            boolean supportPartial = "bytes".equals(acceptRanges);

            if (!supportPartial) {
                logger.warn("Skipping partial download for {} because it is not supported", fileUrl);
                threadNum = 1;
            }

            long fileSize = connection.getContentLengthLong();
            logger.info("Start to download {} (size: {})", fileUrl, formatFileSize(fileSize));

            // 计算每个线程下载的字节范围
            long blockSize = fileSize / threadNum;

            // 创建线程池
            ExecutorService executor = Executors.newFixedThreadPool(threadNum);
            List<DownloadTask> tasks = new ArrayList<>();

            // 创建临时文件目录
            File tempDir = new File(saveFile.getParent(), saveFile.getName() + "_temp");
            if (!tempDir.exists()) {
                tempDir.mkdir();
            }

            // 创建下载任务
            for (int i = 0; i < threadNum; i++) {
                long start = i * blockSize;
                long end = (i == threadNum - 1) ? fileSize - 1 : start + blockSize - 1;

                String partFileName = saveFile.getName() + ".part" + i;
                File partFile = new File(tempDir, partFileName);

                DownloadTask task = new DownloadTask(fileUrl, partFile, start, end, i);
                tasks.add(task);
                executor.execute(task);
            }

            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);

            // 检查所有任务是否完成
            boolean allSuccess = true;
            for (DownloadTask task : tasks) {
                if (!task.isSuccess()) {
                    allSuccess = false;
                    logger.error("Task {} failed", task);
                }
            }

            if (allSuccess) {
                // 合并文件
                mergeFiles(tasks, saveFile);
                // 删除临时目录
                deleteDirectory(tempDir);
                logger.info("Successfully downloaded {} to {}", fileUrl, savePath);
            } else {
                logger.error("Download failed");
            }

        } catch (IOException e) {
            logger.error("Failed to download {}, caused by: {}", fileUrl, e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 合并下载的文件部分
     */
    private static void mergeFiles(List<DownloadTask> tasks, File outputFile) throws IOException {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            for (DownloadTask task : tasks) {
                File partFile = task.getPartFile();
                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(partFile))) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
            }
        }
    }

    /**
     * 删除目录及其内容
     */
    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }

    /**
     * 格式化文件大小显示
     */
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 下载任务类
     */
    static class DownloadTask implements Runnable {
        private final String fileUrl;
        @Getter
        private final File partFile;
        private final long start;
        private final long end;
        @Getter
        private final int threadId;
        @Getter
        private boolean success = false;
        @Getter
        private String errorMessage;

        public DownloadTask(String fileUrl, File partFile, long start, long end, int threadId) {
            this.fileUrl = fileUrl;
            this.partFile = partFile;
            this.start = start;
            this.end = end;
            this.threadId = threadId;
        }

        @Override
        public void run() {
            HttpURLConnection connection = null;
            InputStream input = null;
            RandomAccessFile output = null;

            try {
                URL url = new URL(fileUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IOException("服务器不支持范围请求，响应码: " + connection.getResponseCode());
                }

                // 创建父目录
                File parent = partFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                input = connection.getInputStream();
                output = new RandomAccessFile(partFile, "rw");

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;

                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }

                success = true;
                logger.debug("Thread id {} download completed (size: {})", threadId, formatFileSize(totalRead));

            } catch (IOException e) {
                errorMessage = e.getMessage();
            } finally {
                try {
                    if (input != null) input.close();
                    if (output != null) output.close();
                    if (connection != null) connection.disconnect();
                } catch (IOException e) {
                    logger.error("Failed to close stream, caused by: {}", e.getMessage());
                }
            }
        }

    }
}