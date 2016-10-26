#!/usr/bin/python

# generate the input file with:

import md5
import subprocess

def mayne():
    file_lines = build_file_list()
    filesize_to_filenames = build_num_bytes_index(file_lines)
    # print_map(filesize_to_filenames)
    md5sums_to_filenames = md5_files_with_the_same_filesize(filesize_to_filenames)
    # print_map(md5sums_to_filenames)
    print_identical_md5sums(md5sums_to_filenames)

def build_file_list():
    magic_ls_num_bytes_column = 8
    subprocess.call("find /Users/bmmoore/svn/btab_trunk -name \"*.java\" -ls > btab-trunk-java-files", shell=True)
    subprocess.call("sort -n -k %s btab-trunk-java-files > btab-trunk-java-files.sort" % (magic_ls_num_bytes_column,), shell=True)
    fp = open("btab-trunk-java-files.sort")
    file_lines = fp.readlines()
    return file_lines

def build_num_bytes_index(file_lines):
    filesize_to_filenames = dict()
    magic_ls_filename_start_column = 11
    for line in file_lines:
        tokens = line.split();
        num_bytes = tokens[7]
        filename = " ".join(tokens[magic_ls_filename_start_column:])
        l = filesize_to_filenames.get(num_bytes)
        if not l:
            l = list()
            filesize_to_filenames[num_bytes] = l
        l.append(filename)
    return filesize_to_filenames

def md5_files_with_the_same_filesize(filesize_to_filenames):
    md5sums_to_filenames = dict()
    for (num_bytes, filenames) in filesize_to_filenames.items():
        if(1 < len(filenames)):
            for filename in filenames:
                fp = open(filename)
                mymd5 = md5.md5(fp.read())
                h = mymd5.hexdigest()
                l = md5sums_to_filenames.get(h)
                if not l:
                    l = list()
                    md5sums_to_filenames[h] = l
                l.append(filename)
    return md5sums_to_filenames

def print_identical_md5sums(md5sums_to_filenames):
    num_duplicates = 0
    for (md5sum, filenames) in md5sums_to_filenames.items():
        num_filenames = len(filenames)
        if(1 < num_filenames):
            num_duplicates += (num_filenames - 1)
            print md5sum
            for filename in filenames:
                print "\t%s" % (filename,)
    print "found %s duplicates" % (num_duplicates,)

def print_map(m):
    for (k,v) in m.items():
        print "%s: %s" % (k,v)

if __name__ == "__main__":
    mayne()
