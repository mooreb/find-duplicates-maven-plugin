#!/usr/bin/python

import md5
import os
import shutil
import sys
import uuid
import zipfile

def usage(argv):
    print "Usage: %s war-or-ear.[we]ar" % (argv[0],)
    sys.exit(1)

def mayne(topdir, first_level_dir, first_file):
    # print "topdir is %s" % (topdir,)
    # print "first_level_dir is %s" % (first_level_dir,)
    unzip(first_level_dir, first_file)
    war_files = find_files(first_level_dir, ".war")
    for war_file in war_files:
        # print "war file is %s" % (war_file,)
        unzip(war_file + ".d", war_file)
    jar_files = find_files(first_level_dir, ".jar")
    for jar_file in jar_files:
        # print "jar file is %s" % (jar_file,)
        unzip(jar_file + ".d", jar_file)
    class_files = find_files(first_level_dir, ".class")
    class_file_tuples = parse_class_file_names(first_level_dir, class_files)
    reverse_index = build_reverse_index(class_file_tuples)
    (identical, conflicting) = augment_and_separate_duplicates(reverse_index)
    print "report for %s: %s conflicting duplicate implementations, %s identical duplicate implementations" % (first_file, len(conflicting), len(identical))
    print_duplicates(conflicting, "conflicting implementations")
    print_duplicates(identical, "identical implementations")
    shutil.rmtree(topdir)

def unzip(destdir, file):
    zfile = zipfile.ZipFile(file)
    for name in zfile.namelist():
        if(name.endswith(".war") or
           name.endswith(".jar") or 
           name.endswith(".class")):
            # print "name is %s" % (name,)
            (internal_dirname, filename) = os.path.split(name)
            # print "internal_dirname is %s" % (internal_dirname,)
            # print "filename is %s" % (filename,)
            dirname = os.path.join(destdir, internal_dirname)
            # print "dirname is %s" % (dirname,)
            if not os.path.exists(dirname):
                os.makedirs(dirname)
            # print "Decompressing " + " file=" + file + " name=" + name + " dirname=" + dirname + " destdir=" + destdir
            zfile.extract(name, destdir)

def find_files(d, suffix):
    retval = list()
    for root, dirs, files in os.walk(d, topdown=True):
        for name in files:
            if(name.endswith(suffix)):
                retval.append(os.path.join(root, name))
    return retval

def parse_class_file_names(first_level_dir, class_files):
    retval = list()
    for class_file in class_files:
        size = os.stat(class_file).st_size
        truncated_class_file = class_file.replace(first_level_dir, "", 1)
        # print "truncated_class_file: %s" % (truncated_class_file,)
        if(truncated_class_file.find("/WEB-INF/lib/") >= 0):
            (before, after) = truncated_class_file.split("/WEB-INF/lib/")
            war = before[len("/"):-len(".d")]
            if '' == war:
                war = os.path.basename(first_level_dir)[:-len(".d")]
            components = after.split(os.path.sep)
            jar = components[0][:-len(".d")]
            fqcn_class = ".".join(components[1:])
            fqcn = fqcn_class[:-len(".class")]
        elif(truncated_class_file.find("/WEB-INF/classes/") >= 0):
            (before, after) = truncated_class_file.split("/WEB-INF/classes/")
            war = before[len("/"):-len(".d")]
            if '' == war:
                war = os.path.basename(first_level_dir)[:-len(".d")]
            jar = None
            components = after.split(os.path.sep)
            fqcn = ".".join(components)[:-len(".class")]
        else:
            war = None
            jar = None
            fqcn = None
        t = (class_file, truncated_class_file, war, jar, fqcn, size)
        retval.append(t)
    return retval

def build_reverse_index(class_file_tuples):
    retval = dict()
    for class_file_tuple in class_file_tuples:
        (class_file, truncated_class_file, war, jar, fqcn, size) = class_file_tuple
        if(retval.has_key(fqcn)):
            l = retval[fqcn]
        else:
            l = list()
            retval[fqcn] = l
        l.append(class_file_tuple)
    return retval

def augment_and_separate_duplicates(reverse_index):
    identical = dict()
    conflicting = dict()
    for l in reverse_index.values():
        if(len(l) > 1):
            fqcn = l[0][4]
            first_md5 = None
            all_have_same_md5 = True
            candidates = list()
            for entry in l:
                (class_file, truncated_class_file, war, jar, fqcn, size) = entry
                mymd5 = md5.md5(open(class_file).read()).hexdigest()
                candidate = (class_file, truncated_class_file, war, jar, fqcn, size, mymd5)
                candidates.append(candidate)
                if(first_md5 is None):
                    first_md5 = mymd5
                if(all_have_same_md5 and (mymd5 != first_md5)):
                    all_have_same_md5 = False
            if all_have_same_md5:
                identical[fqcn] = candidates
            else:
                conflicting[fqcn] = candidates
    return (identical,conflicting)

def print_duplicates(reverse_index, label):
    if(reverse_index is None): 
        return
    if(0 == len(reverse_index)):
        return
    i = 0
    sep = "-"*23
    print "%s %s %s" % (sep, label, sep)
    keys = reverse_index.keys()
    keys.sort()
    for k in keys:
        l = reverse_index[k]
        i += 1
        print "DUP %s: %s" % (i, k)
        for entry in l:
            (class_file, truncated_class_file, war, jar, fqcn, size, mymd5) = entry
            print "%20s %30s %10s %32s" % (war, jar, size, mymd5)

if __name__ == "__main__":
    if(2 != len(sys.argv)):
        usage(sys.argv)
    topdir = os.path.join("/tmp", uuid.uuid4().hex)
    first_level_dir = os.path.join(topdir, os.path.basename(sys.argv[1]) + ".d")
    mayne(topdir, first_level_dir, sys.argv[1])
