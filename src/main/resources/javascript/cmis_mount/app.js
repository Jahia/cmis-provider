angular.module('cmisMount', ['folderPicker'])
    .directive('cmisInitiator', [function () {
        return {
            restrict: "A",
            scope: {
                initiator: '=cmisInitiator',
                type: '=cmisType',
                repositoryId: '=cmisRepositoryid'
            },
            controller: function ($scope) {
                $scope.type = $scope.initiator.type;
                $scope.repositoryId = $scope.initiator.repositoryId;
            }
        }
    }])

    .controller('cmisMountEditCtrl', function ($scope, $controller) {
        angular.extend(this, $controller('folderPickerCtrl', {
            $scope: $scope
        }));

        $scope.$watch('cmisType', function(newVal, oldVal) {
            // save old value in case of switch between type
            if(newVal == "alfresco") {
                if(oldVal == "cmis") {
                    $scope.cmisRepoId = angular.copy($scope.repositoryId);
                }
                // keep as default value, will be overrided by the mount point factory
                $scope.repositoryId = "-default-";
            }
            if(newVal == "cmis" && oldVal == "alfresco") {
                $scope.repositoryId = $scope.cmisRepoId;
            }
        })
    });

