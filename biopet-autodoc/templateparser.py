#! /usr/bin/env python2
from jinja2 import Environment, FileSystemLoader
import os
import argparse
import ast
import yaml

if __name__ == "__main__":

    parser=argparse.ArgumentParser(description="")
    parser.add_argument("-t", type=str, nargs=1, help="Templatefile to be used")
    parser.add_argument("-o", type=str, nargs=1, help="output file")
    parser.add_argument("-d", type=str, nargs=1, help="dictionary containing values")
    
    arguments=parser.parse_args()
    template=arguments.t
    output_file=arguments.o
    dictionary=yaml.load(ast.literal_eval(arguments.d))
    
    j2_env = Environment(loader=FileSystemLoader(os.getcwd()))
    rendered = j2_env.get_template(template).render(tool=dictionary)
    output_file=open("test.sh", "w")
    output_file.seek(0)
    output_file.write(rendered)
    output_file.close()
