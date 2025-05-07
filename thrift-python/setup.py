"""
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""
from setuptools import setup, find_packages
from os import path
from sys import version_info
here = path.abspath(path.dirname(__file__))
if version_info.major == 3:
    with open(path.join(here, 'README.md'), encoding='utf-8') as f:
        long_description = f.read()
else:
    with open(path.join(here, 'README.md')) as f:
        long_description = f.read()

setup(
    name='hbase-client-thrift2',
    version='2.0',
    description='Apache HBase thrift2 client.',
    long_description=long_description,
    long_description_content_type='text/markdown',
    # Author details
    author='Apache HBase Community',
    author_email='dev@hbase.apache.org',
    url='',
    classifiers=[
        "License :: OSI Approved :: Apache Software License",
        "Natural Language :: English",
        "Programming Language :: Python :: 2.7",
        "Programming Language :: Python :: 3"
    ],
    packages=find_packages(),
    py_modules=["thbase"],
    install_requires=["thrift==0.13.0", "enum34", "typing", "pure-sasl"]
)