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
class LoginEntry(object):

    def __init__(self, username=None, password=None, service='tcp', mechanism='PLAIN'):
        self._username = username
        self._password = password
        self._service = service
        self._mechanism = mechanism

    @property
    def username(self):
        return self._username

    @property
    def password(self):
        return self._password

    @property
    def service(self):
        return self._service

    @property
    def mechanism(self):
        return self._mechanism
