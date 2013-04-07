/*
 * Copyright 2008-2010 Xebia and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.xebia.workshop.continuousdelivery;

import static com.google.common.collect.Lists.newArrayList;

import java.util.Collection;
import java.util.List;

public class WorkshopInfrastructure {

    private final List<String> teamIdentifiers = newArrayList();
    private String githubAccount;
    private String githubUsername;
    private String githubPassword;
    private String githubRepositoryPrefix;
    private String githubSourceRepository;
    private String nexusDomainName;
    private String nexusPublicIp;
    private String keyPairName;

    private WorkshopInfrastructure() {
    }

    public static Builder create() {
        return new Builder();
    }

    public List<String> getTeamIdentifiers() {
        return teamIdentifiers;
    }

    public int getTeamCount() {
        return teamIdentifiers.size();
    }

    public String getKeyPairName() {
        return keyPairName;
    }

    public String getGithubGuestAccountName() {
        return githubAccount;
    }

    public String getGithubGuestAccountPassword() {
        return githubPassword;
    }

    public String getGithubGuestAccountUrl() {
        return "https://github.com/" + githubAccount + "/";
    }

    public String getGithubGuestAccountUsername() {
        return githubUsername;
    }

    public String getGithubRepositoryNameForTeam(String team) {
        return githubRepositoryPrefix + team;
    }

    public String getGithubSourceRepository() {
        return githubSourceRepository;
    }

    public String getNexusUrlWithDomainName() {
        return "http://" + nexusDomainName + ":8081/nexus/";
    }

    public String getNexusPublicIp() {
        return nexusPublicIp;
    }

    public String getNexusUrlWithIp() {
        return "http://" + nexusPublicIp + ":8081/nexus/";
    }

    public static class Builder {

        private WorkshopInfrastructure infra = new WorkshopInfrastructure();

        public WorkshopInfrastructure build() {
            return infra;
        }

        public Builder withGithubGuestInfo(String account, String username, String password) {
            infra.githubAccount = account;
            infra.githubUsername = username;
            infra.githubPassword = password;
            return this;
        }

        public Builder withNexusDomainName(String domainName) {
            infra.nexusDomainName = domainName;
            return this;
        }

        public Builder withNexusPublicIp(String ip) {
            infra.nexusPublicIp = ip;
            return this;
        }

        public Builder withTeamIdentifiers(Collection<String> teamIdentifiers) {
            infra.teamIdentifiers.addAll(teamIdentifiers);
            return this;
        }

        public Builder withGithubRepositoryPrefix(String prefix) {
            infra.githubRepositoryPrefix = prefix;
            return this;
        }

        public Builder withGithubSourceRepository(String sourceRepo) {
            infra.githubSourceRepository = sourceRepo;
            return this;
        }

        public Builder withKeyPairName(String keyPairName) {
            infra.keyPairName = keyPairName;
            return this;
        }
    }
}
