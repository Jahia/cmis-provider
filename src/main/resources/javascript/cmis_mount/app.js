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
            if(newVal == "alfresco") {
                $scope.repositoryId = "-";
            } else {
                $scope.repositoryId = "";
            }
        })
    });

