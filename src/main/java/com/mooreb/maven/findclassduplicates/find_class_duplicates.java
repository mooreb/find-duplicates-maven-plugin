package com.mooreb.maven.findclassduplicates;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class find_class_duplicates {
    private static void usage() {
        System.err.println("Usage: one argument: war-or-ear.[we]ar");
    }

    private static int[] mayne(final File topdir, final File first_level_dir, final File first_file) throws
            IOException, NoSuchAlgorithmException {
        Map<String, List<X>> allClasses = buildAllClasses(first_level_dir, first_file);
        Y identical_conflicting = compute_md5_and_separate_conflicting_from_identical(allClasses);
        Map<String, List<X>> identical = identical_conflicting.getIdentical();
        Map<String, List<X>> conflicting = identical_conflicting.getConflicting();
        final int num_conflicting_duplicates = conflicting.size();
        final int num_identical_duplicates = identical.size();
        System.out.println(String.format("report for %s: %s conflicting duplicate implementations, " +
                "%s identical duplicate implementations",
                first_file,
                num_conflicting_duplicates,
                num_identical_duplicates));

        print_duplicates(conflicting, "conflicting implementations");
        print_duplicates(identical, "identical implementations");

        System.out.println("Deleting temporary files...");
        recursive_delete(topdir);
        System.out.println("Temporary files deleted");
        
        int[] retval = new int[2];
        retval[0] = num_conflicting_duplicates;
        retval[1] = num_identical_duplicates;
        return retval;
    }

    /*
     * It's sad that I (think I (?)) need this recursive_delete.
     *
     * I could have used apache commons io FileUtils but:
     *   * depending on an external jar in a plugin seemed like it might be problematic:
     *       * does each plugin get its own execution environment?
     *       * how are conflicts dealt with?
     *       * when you find a problem, how do you debug?
     *
     * I would have loved to have used JDK 7 nio, but I feel like I'm stuck on JDK 5/6 to be safe.
     */

    private static void recursive_delete(File f) {
        // System.err.println("deleting " + f.toString());
        final Set<File> failedFiles = new HashSet<File>();
        final Set<File> failedDirectories = new HashSet<File>();
        final Set<File> whoKnows = new HashSet<File>();
        if(f.isFile()) {
            boolean success = f.delete();
            if(!success) {
                failedFiles.add(f);
            }
        }
        else if(f.isDirectory()) {
            final Stack<File> stack = new Stack<File>();
            stack.push(f);
            recursive_delete_helper(stack, failedFiles, failedDirectories, whoKnows);
        }
        else {
            whoKnows.add(f);
        }
        final int numFailedFiles = failedFiles.size();
        if(0 != numFailedFiles) {
            System.err.println("Failed to delete " + numFailedFiles + " files");
            for(final File fail : failedFiles) {
                System.err.println("\t" + fail.toString());
            }
        }
        final int numFailedDirectories = failedDirectories.size();
        if(0 != numFailedDirectories) {
            System.err.println("Failed to delete " + numFailedDirectories + " directories");
            for(final File fail : failedDirectories) {
                System.err.println("\t" + fail.toString());
            }
        }
        final int numWhoKnowsFailures = whoKnows.size();
        if(0 != numWhoKnowsFailures) {
            System.err.println("Failed to delete " + numWhoKnowsFailures + " files of unknown type");
            for(final File fail : whoKnows) {
                System.err.println("\t" + fail.toString());
            }
        }
    }

    private static void recursive_delete_helper(Stack<File> stack,
                                                Set<File> failedFiles,
                                                Set<File> failedDirectories,
                                                Set<File> whoKnows) {
        while(!stack.isEmpty()) {
            File top = stack.pop();
            if(top.isDirectory()) {
                if(failedDirectories.contains(top)) {
                    continue;
                }
                File[] contents = top.listFiles();
                if(null == contents) {
                    failedDirectories.add(top);
                    continue;
                }
                if(0 == contents.length) {
                    File empty_directory = top;
                    boolean success = empty_directory.delete();
                    if(!success) {
                        failedDirectories.add(empty_directory);
                        continue;
                    }
                }
                else { /* non-empty directory */
                    final List<File> encounteredDirectories = new ArrayList<File>();
                    int numFailedFileDeletes = 0;
                    for (final File f : contents) {
                        if (f.isFile()) {
                            final boolean success = f.delete();
                            if(!success) {
                                numFailedFileDeletes++;
                                failedFiles.add(f);
                            }
                        }
                        else if (f.isDirectory()) {
                            encounteredDirectories.add(f);
                        }
                        else {
                            numFailedFileDeletes++;
                            whoKnows.add(f);
                        }
                    }
                    if(numFailedFileDeletes > 0) {
                        // we won't be able to delete this non-empty directory...
                        failedDirectories.add(top);
                        // ... but keep trying to delete the rest of its subdirectories.
                    }

                    boolean allSubdirsFailed = false;
                    for(final File e : encounteredDirectories) {
                        if(failedDirectories.contains(e)) {
                            allSubdirsFailed = true;
                        }
                        else {
                            allSubdirsFailed = false;
                            break;
                        }
                    }
                    if(allSubdirsFailed) {
                        failedDirectories.add(top);
                        continue;
                    }
                    else {
                        stack.push(top);
                        for(final File e : encounteredDirectories) {
                            if(!failedDirectories.contains(e)) {
                                stack.push(e);
                            }
                        }
                    }
                }
            }
            else {
                whoKnows.add(top);
            }
        }
    }

    private static Y compute_md5_and_separate_conflicting_from_identical(Map<String, List<X>> allClasses) throws NoSuchAlgorithmException, IOException {
        final Map<String, List<X>> identical = new HashMap<String, List<X>>();
        final Map<String, List<X>> conflicting = new HashMap<String, List<X>>();
        for(Map.Entry<String, List<X>> entry : allClasses.entrySet()) {
            final String fcqn = entry.getKey();
            final List<X> classes = entry.getValue();
            if(classes.size() > 1) {
                boolean all_have_the_same_md5 = true;
                String first_md5 = null;
                for(final X x : classes) {
                    final String md5 = compute_md5(x.getClassfile());
                    x.setMD5(md5);
                    if(null == first_md5) { first_md5 = md5; }
                    if(all_have_the_same_md5 && !md5.equals(first_md5)) {
                        all_have_the_same_md5 = false;
                    }
                }
                if(all_have_the_same_md5) {
                    identical.put(fcqn, classes);
                }
                else {
                    conflicting.put(fcqn, classes);
                }
            }
        }
        return new Y(identical, conflicting);
    }

    private static Map<String, List<X>> buildAllClasses(File first_level_dir, File first_file) throws IOException {
        final Map<String, List<X>> allClasses = new HashMap<String, List<X>>();
        final Z first_wars_jars_classes = unzip(first_file, first_level_dir);
        final Set<File> first_wars = first_wars_jars_classes.getWars();
        for (final File war : first_wars) {
            final Z inner_jars_classes = unzip(war, new File(war.toString() + ".d"));
            final Map<String, File> innerClasses = inner_jars_classes.getClasses();
            add(innerClasses, null, war, allClasses);
            final Set<File> innerJars = inner_jars_classes.getJars();
            for (final File innerJar : innerJars) {
                final Z inner_inner_classes = unzip(innerJar, new File(innerJar.toString() + ".d"));
                final Map<String, File> innerInnerClasses = inner_inner_classes.getClasses();
                add(innerInnerClasses, innerJar, war, allClasses);
            }
        }
        final Set<File> first_jars = first_wars_jars_classes.getJars();
        for (final File jar : first_jars) {
            final Z inner_classes = unzip(jar, new File(jar.toString() + ".d"));
            final Map<String, File> innerClasses = inner_classes.getClasses();
            add(innerClasses, jar, first_file, allClasses);
        }
        final Map<String, File> first_classes = first_wars_jars_classes.getClasses();
        add(first_classes, null, first_file, allClasses);
        return allClasses;
    }

    private static void add(Map<String, File> classes, File jar, File war, Map<String, List<X>> allClasses) {
        for(final String fqcn : classes.keySet()) {
            List<X> l;
            if(allClasses.containsKey(fqcn)) {
                l = allClasses.get(fqcn);
            }
            else {
                l = new ArrayList<X>();
                allClasses.put(fqcn, l);
            }
            l.add(new X(war, jar, classes.get(fqcn)));
        }
    }

    private static class X {
        private final File war;
        private final File jar;
        private final File classfile;
        private String md5 = null;

        private X(File war, File jar, File classfile) {
            this.war = war;
            this.jar = jar;
            this.classfile = classfile;
        }

        public File getWar() {
            return war;
        }

        public File getJar() {
            return jar;
        }

        public File getClassfile() {
            return classfile;
        }

        public String getMD5() {
            return md5;
        }

        public void setMD5(String md5) {
            this.md5 = md5;

        }
    }

    private static class Y {
        private final Map<String, List<X>> identical;
        private final Map<String, List<X>> conflicting;
        private Y(Map<String, List<X>> identical, Map<String, List<X>> conflicting) {
            this.identical = identical;
            this.conflicting = conflicting;
        }

        public Map<String, List<X>> getIdentical() {
            return identical;
        }

        public Map<String, List<X>> getConflicting() {
            return conflicting;
        }
    }

    private static class Z {
        private final Set<File> wars;
        private final Set<File> jars;
        private final Map<String, File> classes;
        private Z(Set<File> wars, Set<File> jars, Map<String, File> classes) {
            this.wars = wars;
            this.jars = jars;
            this.classes = classes;
        }
        public Set<File> getWars() { return wars; }
        public Set<File> getJars() { return jars; }
        public Map<String, File> getClasses() { return classes; }
    }

    private static Z unzip(final File zipFileFile,
                              final File destDir
    ) throws IOException {
        ZipFile zipFile = new ZipFile(zipFileFile);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        final Set<File> wars = new HashSet<File>();
        final Set<File> jars = new HashSet<File>();
        final Map<String, File> classes = new HashMap<String, File>();
        final Z retval = new Z(wars, jars, classes);
        while(entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            if(entry.isDirectory()) { continue; }
            final String entryName = entry.getName();
            final String[] components = entryName.split("/");
            final File output_file = build_output_file(destDir, components);

            if(entryName.endsWith(".class")) {
                final String prefix = "WEB-INF/classes/";
                String fqcn = entryName.substring(0, entryName.length()-".class".length()).replace("/", ".");
                if(entryName.startsWith(prefix)) {
                    fqcn = fqcn.substring(prefix.length());
                }
                classes.put(fqcn, output_file);
            }
            else if(entryName.endsWith(".jar")) {
                jars.add(output_file);
            }
            else if(entryName.endsWith(".war")) {
                wars.add(output_file);
            }
            else {
                continue;
            }

            final InputStream inputStream = zipFile.getInputStream(entry);
            final FileOutputStream fos = new FileOutputStream(output_file);
            final byte[] buffer = new byte[65536];
            int bytes_read;
            while ((bytes_read = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytes_read);
            }
            fos.flush();
            fos.close();
            inputStream.close();
        }
        zipFile.close();
        
        return retval;
    }

    private static final File build_output_file(File root, String[] components) {
        File retval = root;
        for(String component : components) {
            retval = new File(retval, component);
        }
        final boolean ignored_success = retval.getParentFile().mkdirs();
        return retval;
    }

    private static final String compute_md5(final File f) throws NoSuchAlgorithmException, FileNotFoundException, IOException {
        final MessageDigest md = MessageDigest.getInstance("MD5");
        final InputStream inputStream = new FileInputStream(f);
        int bytes_read;
        byte[] buffer = new byte[65535];
        while ((bytes_read = inputStream.read(buffer)) != -1) {
            md.update(buffer, 0, bytes_read);
        }
        inputStream.close();
        byte[] digest = md.digest();
        final BigInteger bigInteger = new BigInteger(1, digest);
        String hex = bigInteger.toString(16);
        while(hex.length() < 32) {
            hex = "0" + hex;
        }
        return hex;
    }

   private static void print_duplicates(Map<String, List<X>> duplicates, String label) {
	    if(0 == duplicates.size()) { return; }
        final String sep = "-----------------------";
        System.out.println(String.format("%s %s %s", sep, label, sep));
        String[] fqcns = duplicates.keySet().toArray(new String[0]);
        Arrays.sort(fqcns);
        int i = 1;
        for(String fqcn : fqcns) {
            System.out.println("DUP " + i++ + ": " + fqcn);
            List<X> classes = duplicates.get(fqcn);
            for(X x : classes) {
                final File war = x.getWar();
                final File jar = x.getJar();
                final String warName = ((null == war) ? "None" : war.getName());
                final String jarName = ((null == jar) ? "None" : jar.getName());
                System.out.println(String.format("%20s %30s %10d %32s", warName, jarName, x.getClassfile().length(), x.getMD5()));
            }
        }
    }

    public static int[] maine(final String first_file_string) throws IOException, NoSuchAlgorithmException {
	final File first_file = new File(first_file_string);
	final UUID uuid = UUID.randomUUID();
	final String tmp = System.getProperty("java.io.tmpdir");
	final File topdir = new File(tmp, uuid.toString());
	final File first_level_dir = new File(topdir, first_file.getName() + ".d");
	final int[] conflicting_identical_counts = mayne(topdir, first_level_dir, first_file);
	return conflicting_identical_counts;
    }

    public static void main(String[] argv) throws IOException, NoSuchAlgorithmException {
        if(1 != argv.length) {
            usage();
            return;
        }
        else {
            final String first_file_string = argv[0];
	    maine(first_file_string);
        }
    }
}
