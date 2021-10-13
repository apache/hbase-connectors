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
from sys import version_info


def to_bytes(origin):
	if version_info.major == 3:
		return bytes(origin, 'utf-8') if isinstance(origin, str) else origin
	return bytes(origin) if isinstance(origin, str) else origin


def to_str(origin):
	if version_info.major == 3:
		return origin.decode('utf-8') if isinstance(origin, bytes) else origin
	return str(origin) if isinstance(origin, bytes) else origin
