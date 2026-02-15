.PHONY: install run test clean docker-build docker-run

install:
	pip install -r requirements.txt

run:
	uvicorn src.main:app --reload --port 8004

test:
	pytest test_setup.py -v

clean:
	find . -type d -name __pycache__ -exec rm -rf {} +
	find . -type f -name "*.pyc" -delete
	find . -type d -name "*.egg-info" -exec rm -rf {} +

docker-build:
	docker build -t search-service .

docker-run:
	docker run -p 8004:8004 --env-file .env search-service
