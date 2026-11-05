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
from thbase.hbase.ttypes import TTableDescriptor
from thbase.hbase.ttypes import TColumnFamilyDescriptor
from thbase.hbase.ttypes import TCompressionAlgorithm
from thbase.hbase.ttypes import TBloomFilterType
from thbase.hbase.ttypes import TDataBlockEncoding
from thbase.hbase.ttypes import TDurability
from thbase.util.bytes import to_bytes
from thbase.util.bytes import to_str
from thbase.util import type_check
from thbase.util import check_none
from thbase.util import str_to_tablename

COMPRESSION_TYPES = [
    TCompressionAlgorithm.NONE,
    TCompressionAlgorithm.GZ,
    TCompressionAlgorithm.LZ4,
    TCompressionAlgorithm.LZO,
    TCompressionAlgorithm.BZIP2,
    TCompressionAlgorithm.SNAPPY,
    TCompressionAlgorithm.ZSTD,
]

BLOOMFILTER_TYPES = [
    TBloomFilterType.NONE,
    TBloomFilterType.ROW,
    TBloomFilterType.ROWCOL,
    TBloomFilterType.ROWPREFIX_FIXED_LENGTH
]

ENCODING_TYPES = [
    TDataBlockEncoding.NONE,
    TDataBlockEncoding.DIFF,
    TDataBlockEncoding.FAST_DIFF,
    TDataBlockEncoding.PREFIX,
    TDataBlockEncoding.ROW_INDEX_V1
]


class ColumnDescriptorBuilder(object):

    def __init__(self, name=None):
        type_check(name, str)
        self.name = name
        self.attributes = {}
        self.configuration = {}
        self.blockSize = None
        self.bloomnFilterType = None
        self.compressionType = None
        self.dfsReplication = None
        self.dataBlockEncoding = None
        self.keepDeletedCells = None
        self.maxVersions = None
        self.minVersions = None
        self.scope = None
        self.timeToLive = None
        self.blockCacheEnabled = None
        self.cacheBloomsOnWrite = None
        self.cacheDataOnWrite = None
        self.cacheIndexesOnWrite = None
        self.compressTags = None
        self.evictBlocksOnClose = None
        self.inMemory = None

    def build(self):
        return TColumnFamilyDescriptor(name=to_bytes(self.name), attributes=self.attributes,
                                       configuration=self.configuration,
                                       blockSize=self.blockSize, bloomnFilterType=self.bloomnFilterType,
                                       compressionType=self.compressionType, dfsReplication=self.dfsReplication,
                                       dataBlockEncoding=self.dataBlockEncoding, keepDeletedCells=self.keepDeletedCells,
                                       maxVersions=self.maxVersions, minVersions=self.minVersions, scope=self.scope,
                                       timeToLive=self.timeToLive, blockCacheEnabled=self.blockCacheEnabled,
                                       cacheBloomsOnWrite=self.cacheBloomsOnWrite,
                                       cacheDataOnWrite=self.cacheDataOnWrite,
                                       cacheIndexesOnWrite=self.cacheIndexesOnWrite, compressTags=self.compressTags,
                                       evictBlocksOnClose=self.evictBlocksOnClose, inMemory=self.inMemory)

    def copy_from_exist(self, desc):
        type_check(desc, TColumnFamilyDescriptor)
        self.name = desc.name
        self.attributes = desc.attributes
        self.configuration = desc.configuration
        self.blockSize = desc.blockSize
        self.bloomnFilterType = desc.bloomnFilterType
        self.compressionType = desc.compressionType
        self.dfsReplication = desc.dfsReplication
        self.dataBlockEncoding = desc.dataBlockEncoding
        self.keepDeletedCells = desc.keepDeletedCells
        self.maxVersions = desc.maxVersions
        self.minVersions = desc.minVersions
        self.scope = desc.scope
        self.timeToLive = desc.timeToLive
        self.blockCacheEnabled = desc.blockCacheEnabled if desc.blockCacheEnabled else None
        self.cacheBloomsOnWrite = desc.cacheBloomsOnWrite if desc.cacheBloomsOnWrite else None
        self.cacheDataOnWrite = desc.cacheDataOnWrite if desc.cacheDataOnWrite else None
        self.cacheIndexesOnWrite = desc.cacheIndexesOnWrite if desc.cacheIndexesOnWrite else None
        self.compressTags = desc.compressTags if desc.compressTags else None
        self.evictBlocksOnClose = desc.evictBlocksOnClose
        self.inMemory = desc.inMemory if desc.inMemory else None

    def add_attribute(self, key, value):
        check_none(key, "Attribute name is None.")
        if value is None and key in self.attributes:
            self.attributes.pop(key)
        else:
            self.attributes[to_bytes(key)] = to_bytes(value)
        return self

    def add_configuration(self, key, value):
        check_none(key, "Configuration key is None.")
        if value is None and key in self.configuration:
            self.configuration.pop(key)
        else:
            self.configuration[to_bytes(key)] = to_bytes(value)
        return self

    def set_compressionType(self, compress_type):
        if compress_type not in COMPRESSION_TYPES:
            raise RuntimeError("Unknown compression algorithm {}".format(compress_type))
        self.compressionType = compress_type
        return self

    def set_blockSize(self, bs):
        type_check(bs, int)
        if bs < 1:
            raise RuntimeError("Illegal block size " + bs)
        self.blockSize = bs
        return self

    def set_bloomnFilterType(self, bf):
        if bf not in BLOOMFILTER_TYPES:
            raise RuntimeError("Unknown bloom filter type {}".format(bf))
        self.bloomnFilterType = bf
        return self

    def set_dfsReplication(self, hr):
        type_check(hr, int)
        if hr < 1:
            raise RuntimeError("Illegal number of dfs replication {}".format(hr))
        self.dfsReplication = hr

    def set_dataBlockEncoding(self, encoding_type):
        if encoding_type not in ENCODING_TYPES:
            raise RuntimeError("Illegal data block encoding type {}".format(encoding_type))
        self.dataBlockEncoding = encoding_type
        return self

    def set_keepDeletedCells(self, kdc):
        type_check(kdc, bool)
        self.keepDeletedCells = kdc
        return self

    def set_maxVersions(self, mv):
        type_check(mv, int)
        if mv < 1:
            raise RuntimeError("Illegal max versions number {}".format(mv))
        self.maxVersions = mv
        return self

    def set_minVersions(self, mv):
        type_check(mv, int)
        if mv < 0:
            raise RuntimeError("Illegal min versions number {}".format(mv))
        self.minVersions = mv
        return self

    def set_scope(self, scope):
        type_check(scope, int)
        if scope < 0:
            raise RuntimeError("Illegal replication scope value {}".format(scope))
        self.scope = scope
        return self

    def set_timeToLive(self, ttl):
        type_check(ttl, int)
        if ttl < 1:
            raise RuntimeError("Illegal TTL value {}".format(ttl))
        self.timeToLive = ttl
        return self

    def set_blockCacheEnabled(self, enabled):
        type_check(enabled, bool)
        self.blockCacheEnabled = enabled
        return self

    def set_cacheBloomsOnWrite(self, value):
        type_check(value, bool)
        self.cacheBloomsOnWrite = value
        return self

    def set_cacheDataOnWrite(self, value):
        type_check(value, bool)
        self.cacheBloomsOnWrite = value
        return self

    def set_cacheIndexesOnWrite(self, value):
        type_check(value, bool)
        self.cacheIndexesOnWrite = value
        return self

    def set_compressTags(self, value):
        type_check(value, bool)
        self.compressTags = value
        return self

    def set_evictBlocksOnClose(self, value):
        type_check(value, bool)
        self.evictBlocksOnClose = value
        return self

    def set_inMemory(self, value):
        type_check(value, bool)
        self.inMemory = value
        return self


DURABILITY_TYPES = [
    TDurability.SKIP_WAL,
    TDurability.SYNC_WAL,
    TDurability.ASYNC_WAL,
    TDurability.FSYNC_WAL,
    TDurability.USE_DEFAULT
]


class TableDescriptorBuilder(object):

    def __init__(self, name):
        self.name = name
        self.columns = []
        self.attributes = {}
        self.durability = None

    def build(self):
        return TTableDescriptor(tableName=str_to_tablename(self.name),
                                columns=self.columns,
                                attributes=self.attributes,
                                durability=self.durability)

    def copy_from_exist(self, desc):
        type_check(desc, TTableDescriptor)
        self.name = ':'.join([to_str(desc.tableName.ns), to_str(desc.tableName.qualifier)])
        self.columns = desc.columns

    def add_column(self, col):
        type_check(col, TColumnFamilyDescriptor)
        for i in range(len(self.columns)):
            if col.name == self.columns[i].name:
                self.columns[i] = col
                return self
        self.columns.append(col)
        return self

    def add_attributes(self, key, value):
        check_none(key, "None key found.")
        if value is None:
            if key in self.attributes:
                self.attributes.pop(key)
        else:
            self.attributes[to_bytes(key)] = to_bytes(value)
        return self

    def set_durability(self, value):
        if value not in DURABILITY_TYPES:
            raise RuntimeError("Illegal durability type {}".format(value))
        self.durability = value
        return self
