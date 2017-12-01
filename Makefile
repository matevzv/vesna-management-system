ci:
	ansible-playbook deploy.yml
	
test:
	ansible-playbook deploy-test.yml
