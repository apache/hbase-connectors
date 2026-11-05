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
from thbase.hbase.ttypes import TTableName
from thbase.util.bytes import to_bytes

__all__ = ['executor', 'handlers', 'type_check', 'check_none', 'str_to_tablename']

DELIMITER = ':'


def type_check(var, t):
    if not isinstance(var, t):
        raise TypeError("A {} object is needed, but got a {}.".format(t.__class, type(var)))


def check_none(var, ms):
    if var is None:
        raise ValueError(ms)


def str_to_tablename(name):
    check_none(name, "")
    type_check(name, str)
    names = name.split(DELIMITER)
    if len(names) == 1:
        return TTableName(ns=to_bytes('default'), qualifier=to_bytes(names[0]))
    elif len(names) == 2:
        return TTableName(ns=to_bytes(names[0]), qualifier=to_bytes(names[1]))
    else:
        raise RuntimeError("Get table name with wrong format.")


def tablename_to_str(table):
    type_check(table, TTableName)
    name = DELIMITER.join()

